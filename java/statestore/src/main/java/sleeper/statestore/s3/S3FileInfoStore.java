/*
 * Copyright 2022-2023 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sleeper.statestore.s3;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.core.record.Record;
import sleeper.core.schema.Field;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.LongType;
import sleeper.core.schema.type.StringType;
import sleeper.core.statestore.FileInfo;
import sleeper.core.statestore.FileInfoStore;
import sleeper.core.statestore.StateStoreException;
import sleeper.io.parquet.record.ParquetReaderIterator;
import sleeper.io.parquet.record.ParquetRecordReader;
import sleeper.io.parquet.record.ParquetRecordWriterFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sleeper.statestore.s3.S3RevisionUtils.RevisionId;
import static sleeper.statestore.s3.S3StateStore.FIRST_REVISION;

class S3FileInfoStore implements FileInfoStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3FileInfoStore.class);
    private static final Schema FILE_SCHEMA = initialiseFileInfoSchema();

    private final int garbageCollectorDelayBeforeDeletionInMinutes;
    private final String stateStorePath;
    private final Configuration conf;
    private final S3RevisionUtils s3RevisionUtils;
    private Clock clock = Clock.systemUTC();

    private S3FileInfoStore(Builder builder) {
        this.stateStorePath = Objects.requireNonNull(builder.stateStorePath, "stateStorePath must not be null");
        this.garbageCollectorDelayBeforeDeletionInMinutes = builder.garbageCollectorDelayBeforeDeletionInMinutes;
        this.conf = Objects.requireNonNull(builder.conf, "hadoopConfiguration must not be null");
        this.s3RevisionUtils = Objects.requireNonNull(builder.s3RevisionUtils, "s3RevisionUtils must not be null");
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public void addFile(FileInfo fileInfo) throws StateStoreException {
        addFiles(Collections.singletonList(fileInfo));
    }

    @Override
    public void addFiles(List<FileInfo> fileInfos) throws StateStoreException {
        long updateTime = clock.millis();
        for (FileInfo fileInfo : fileInfos) {
            if (null == fileInfo.getFilename()
                    || null == fileInfo.getFileStatus()
                    || null == fileInfo.getPartitionId()
                    || null == fileInfo.getNumberOfRecords()) {
                throw new IllegalArgumentException("FileInfo needs non-null filename, status, partition id and number of records: got " + fileInfo);
            }
        }
        Function<List<FileInfo>, List<FileInfo>> update = list -> {
            list.addAll(setLastUpdateTimes(fileInfos, updateTime));
            return list;
        };
        try {
            updateFiles(update);
        } catch (IOException e) {
            throw new StateStoreException("IOException updating file infos", e);
        }
    }

    @Override
    public void atomicallyUpdateFilesToReadyForGCAndCreateNewActiveFile(List<FileInfo> filesToBeMarkedReadyForGC, FileInfo newActiveFile)
            throws StateStoreException {
        long updateTime = clock.millis();
        Set<String> namesOfFilesToBeMarkedReadyForGC = filesToBeMarkedReadyForGC.stream()
                .map(FileInfo::getFilename)
                .collect(Collectors.toSet());

        Function<List<FileInfo>, String> condition = list -> {
            Map<String, FileInfo> fileNameToFileInfo = new HashMap<>();
            list.forEach(f -> fileNameToFileInfo.put(f.getFilename(), f));
            for (FileInfo fileInfo : filesToBeMarkedReadyForGC) {
                if (!fileNameToFileInfo.containsKey(fileInfo.getFilename())
                        || !fileNameToFileInfo.get(fileInfo.getFilename()).getFileStatus().equals(FileInfo.FileStatus.ACTIVE)) {
                    return "Files in filesToBeMarkedReadyForGC should be active: file " + fileInfo.getFilename() + " is not active";
                }
            }
            return "";
        };

        Function<List<FileInfo>, List<FileInfo>> update = list -> {
            List<FileInfo> filteredFiles = new ArrayList<>();
            for (FileInfo fileInfo : list) {
                if (namesOfFilesToBeMarkedReadyForGC.contains(fileInfo.getFilename())) {
                    fileInfo = fileInfo.toBuilder()
                            .fileStatus(FileInfo.FileStatus.READY_FOR_GARBAGE_COLLECTION)
                            .lastStateStoreUpdateTime(updateTime)
                            .build();
                }
                filteredFiles.add(fileInfo);
            }
            filteredFiles.add(setLastUpdateTime(newActiveFile, updateTime));
            return filteredFiles;
        };

        try {
            updateFiles(update, condition);
        } catch (IOException e) {
            throw new StateStoreException("IOException updating file infos", e);
        }
    }

    @Override
    public void atomicallyUpdateFilesToReadyForGCAndCreateNewActiveFiles(List<FileInfo> filesToBeMarkedReadyForGC,
                                                                         FileInfo leftFileInfo,
                                                                         FileInfo rightFileInfo) throws StateStoreException {
        long updateTime = clock.millis();
        Set<String> namesOfFilesToBeMarkedReadyForGC = new HashSet<>();
        filesToBeMarkedReadyForGC.stream().map(FileInfo::getFilename).forEach(namesOfFilesToBeMarkedReadyForGC::add);

        Function<List<FileInfo>, String> condition = list -> {
            Map<String, FileInfo> fileNameToFileInfo = new HashMap<>();
            list.forEach(f -> fileNameToFileInfo.put(f.getFilename(), f));
            for (FileInfo fileInfo : filesToBeMarkedReadyForGC) {
                if (!fileNameToFileInfo.containsKey(fileInfo.getFilename())
                        || !fileNameToFileInfo.get(fileInfo.getFilename()).getFileStatus().equals(FileInfo.FileStatus.ACTIVE)) {
                    return "Files in filesToBeMarkedReadyForGC should be active: file " + fileInfo.getFilename() + " is not active";
                }
            }
            return "";
        };

        Function<List<FileInfo>, List<FileInfo>> update = list -> {
            List<FileInfo> filteredFiles = new ArrayList<>();
            for (FileInfo fileInfo : list) {
                if (namesOfFilesToBeMarkedReadyForGC.contains(fileInfo.getFilename())) {
                    fileInfo = fileInfo.toBuilder()
                            .fileStatus(FileInfo.FileStatus.READY_FOR_GARBAGE_COLLECTION)
                            .lastStateStoreUpdateTime(updateTime)
                            .build();
                }
                filteredFiles.add(fileInfo);
            }
            filteredFiles.add(setLastUpdateTime(leftFileInfo, updateTime));
            filteredFiles.add(setLastUpdateTime(rightFileInfo, updateTime));
            return filteredFiles;
        };
        try {
            updateFiles(update, condition);
        } catch (IOException e) {
            throw new StateStoreException("IOException updating file infos", e);
        }
    }

    @Override
    public void atomicallyUpdateJobStatusOfFiles(String jobId, List<FileInfo> fileInfos) throws StateStoreException {
        long updateTime = clock.millis();
        Set<String> namesOfFiles = new HashSet<>();
        fileInfos.stream().map(FileInfo::getFilename).forEach(namesOfFiles::add);

        Function<List<FileInfo>, String> condition = list -> {
            Map<String, FileInfo> fileNameToFileInfo = new HashMap<>();
            list.forEach(f -> fileNameToFileInfo.put(f.getFilename(), f));
            for (FileInfo fileInfo : fileInfos) {
                if (!fileNameToFileInfo.containsKey(fileInfo.getFilename())
                        || null != fileNameToFileInfo.get(fileInfo.getFilename()).getJobId()) {
                    return "Files should have a null job status: file " + fileInfo.getFilename() + " doesn't meet this criteria";
                }
            }
            return "";
        };

        Function<List<FileInfo>, List<FileInfo>> update = list -> {
            List<FileInfo> filteredFiles = new ArrayList<>();
            for (FileInfo fileInfo : list) {
                if (namesOfFiles.contains(fileInfo.getFilename())) {
                    fileInfo = fileInfo.toBuilder().jobId(jobId)
                            .lastStateStoreUpdateTime(updateTime)
                            .build();
                }
                filteredFiles.add(fileInfo);
            }
            return filteredFiles;
        };

        try {
            updateFiles(update, condition);
        } catch (IOException e) {
            throw new StateStoreException("IOException updating file infos", e);
        } catch (StateStoreException e) {
            throw new StateStoreException("StateStoreException updating jobid of files");
        }
    }

    @Override
    public void deleteReadyForGCFile(FileInfo readyForGCFileInfo) throws StateStoreException {
        long updateTime = clock.millis();
        Function<List<FileInfo>, String> condition = list -> {
            Map<String, FileInfo> fileNameToFileInfo = new HashMap<>();
            list.forEach(f -> fileNameToFileInfo.put(f.getFilename(), f));

            FileInfo currentFileInfo = fileNameToFileInfo.get(readyForGCFileInfo.getFilename());
            if (!currentFileInfo.getFileStatus().equals(FileInfo.FileStatus.READY_FOR_GARBAGE_COLLECTION)) {
                return "File to be deleted should be marked as ready for GC, got " + currentFileInfo.getFileStatus();
            }
            return "";
        };

        Function<List<FileInfo>, List<FileInfo>> update = list -> {
            List<FileInfo> filteredFiles = new ArrayList<>();
            for (FileInfo fileInfo : list) {
                if (!readyForGCFileInfo.getFilename().equals(fileInfo.getFilename())) {
                    filteredFiles.add(setLastUpdateTime(fileInfo, updateTime));
                }
            }
            return filteredFiles;
        };

        try {
            updateFiles(update, condition);
        } catch (IOException e) {
            throw new StateStoreException("IOException updating file infos", e);
        }
    }

    @Override
    public List<FileInfo> getActiveFiles() throws StateStoreException {
        // TODO Optimise the following by pushing the predicate down to the Parquet reader
        RevisionId revisionId = getCurrentFilesRevisionId();
        if (null == revisionId) {
            return Collections.emptyList();
        }
        try {
            List<FileInfo> fileInfos = readFileInfosFromParquet(getFilesPath(revisionId));
            return fileInfos.stream().filter(f -> f.getFileStatus().equals(FileInfo.FileStatus.ACTIVE)).collect(Collectors.toList());
        } catch (IOException e) {
            throw new StateStoreException("IOException retrieving active files", e);
        }
    }

    @Override
    public Iterator<FileInfo> getReadyForGCFiles() throws StateStoreException {
        // TODO Optimise the following by pushing the predicate down to the Parquet reader
        try {
            long delayInMilliseconds = 1000L * 60L * garbageCollectorDelayBeforeDeletionInMinutes;
            long deleteTime = clock.millis() - delayInMilliseconds;
            List<FileInfo> fileInfos = readFileInfosFromParquet(getFilesPath(getCurrentFilesRevisionId()));
            List<FileInfo> filesReadyForGC = fileInfos.stream().filter(f -> {
                if (!f.getFileStatus().equals(FileInfo.FileStatus.READY_FOR_GARBAGE_COLLECTION)) {
                    return false;
                }
                long lastUpdateTime = f.getLastStateStoreUpdateTime();
                return lastUpdateTime < deleteTime;
            }).collect(Collectors.toList());
            return filesReadyForGC.iterator();
        } catch (IOException e) {
            throw new StateStoreException("IOException retrieving ready for GC files", e);
        }
    }

    @Override
    public List<FileInfo> getActiveFilesWithNoJobId() throws StateStoreException {
        // TODO Optimise the following by pushing the predicate down to the Parquet reader
        try {
            List<FileInfo> fileInfos = readFileInfosFromParquet(getFilesPath(getCurrentFilesRevisionId()));
            return fileInfos.stream().filter(f -> {
                if (!f.getFileStatus().equals(FileInfo.FileStatus.ACTIVE)) {
                    return false;
                }
                return null == f.getJobId();
            }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new StateStoreException("IOException retrieving active files with no job id", e);
        }
    }

    @Override
    public Map<String, List<String>> getPartitionToActiveFilesMap() throws StateStoreException {
        List<FileInfo> files = getActiveFiles();
        Map<String, List<String>> partitionToFiles = new HashMap<>();
        for (FileInfo fileInfo : files) {
            String partition = fileInfo.getPartitionId();
            if (!partitionToFiles.containsKey(partition)) {
                partitionToFiles.put(partition, new ArrayList<>());
            }
            partitionToFiles.get(partition).add(fileInfo.getFilename());
        }
        return partitionToFiles;
    }

    private void updateFiles(Function<List<FileInfo>, List<FileInfo>> update) throws IOException, StateStoreException {
        updateFiles(update, l -> "");
    }

    private void updateFiles(Function<List<FileInfo>, List<FileInfo>> update, Function<List<FileInfo>, String> condition)
            throws IOException, StateStoreException {
        int numberAttempts = 0;
        while (numberAttempts < 10) {
            RevisionId revisionId = getCurrentFilesRevisionId();
            String filesPath = getFilesPath(revisionId);
            List<FileInfo> files;
            try {
                files = readFileInfosFromParquet(filesPath);
                LOGGER.debug("Attempt number {}: reading file information (revisionId = {}, path = {})",
                        numberAttempts, revisionId, filesPath);
            } catch (IOException e) {
                LOGGER.debug("IOException thrown attempting to read file information; retrying");
                numberAttempts++;
                sleep(numberAttempts);
                continue;
            }

            // Check condition
            String conditionCheck = condition.apply(files);
            if (!conditionCheck.isEmpty()) {
                throw new StateStoreException("Conditional check failed: " + conditionCheck);
            }

            // Apply update
            List<FileInfo> updatedFiles = update.apply(files);
            LOGGER.debug("Applied update to file information");

            // Attempt to write update
            RevisionId nextRevisionId = s3RevisionUtils.getNextRevisionId(revisionId);
            String nextRevisionIdPath = getFilesPath(nextRevisionId);
            try {
                LOGGER.debug("Writing updated file information (revisionId = {}, path = {})",
                        nextRevisionId, nextRevisionIdPath);
                writeFileInfosToParquet(updatedFiles, nextRevisionIdPath);
            } catch (IOException e) {
                LOGGER.debug("IOException thrown attempting to write file information; retrying");
                numberAttempts++;
                continue;
            }
            try {
                conditionalUpdateOfFileInfoRevisionId(revisionId, nextRevisionId);
                LOGGER.debug("Updated file information to revision {}", nextRevisionId);
                break;
            } catch (ConditionalCheckFailedException e) {
                LOGGER.info("Attempt number {} to update files failed with conditional check failure, deleting file {} and retrying ({}) ",
                        numberAttempts, nextRevisionIdPath, e.getMessage());
                Path path = new Path(nextRevisionIdPath);
                path.getFileSystem(conf).delete(path, false);
                LOGGER.info("Deleted file {}", path);
                numberAttempts++;
                sleep(numberAttempts);
            }
        }
    }

    private void sleep(int n) {
        // Implements exponential back-off with jitter, see
        // https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
        int sleepTimeInSeconds = (int) Math.min(120, Math.pow(2.0, n + 1));
        long sleepTimeWithJitter = (long) (Math.random() * sleepTimeInSeconds * 1000L);
        try {
            Thread.sleep(sleepTimeWithJitter);
        } catch (InterruptedException e) {
            // Do nothing
        }
    }


    private RevisionId getCurrentFilesRevisionId() {
        return s3RevisionUtils.getCurrentFilesRevisionId();
    }

    private void conditionalUpdateOfFileInfoRevisionId(RevisionId currentRevisionId, RevisionId newRevisionId) {
        s3RevisionUtils.conditionalUpdateOfFileInfoRevisionId(currentRevisionId, newRevisionId);
    }

    private static Schema initialiseFileInfoSchema() {
        return Schema.builder()
                .rowKeyFields(new Field("fileName", new StringType()))
                .valueFields(
                        new Field("fileStatus", new StringType()),
                        new Field("partitionId", new StringType()),
                        new Field("lastStateStoreUpdateTime", new LongType()),
                        new Field("numberOfRecords", new LongType()),
                        new Field("jobId", new StringType()))
                .build();
    }

    public void initialise() throws StateStoreException {
        RevisionId firstRevisionId = new RevisionId(FIRST_REVISION, UUID.randomUUID().toString());
        String path = getFilesPath(firstRevisionId);
        try {
            writeFileInfosToParquet(Collections.emptyList(), path);
            LOGGER.debug("Written initial empty file to {}", path);
        } catch (IOException e) {
            throw new StateStoreException("IOException writing files to file " + path, e);
        }
        s3RevisionUtils.saveFirstFilesRevision(firstRevisionId);
    }

    @Override
    public boolean hasNoFiles() {
        RevisionId revisionId = getCurrentFilesRevisionId();
        if (revisionId == null) {
            return true;
        }
        String path = getFilesPath(revisionId);
        try (ParquetReader<Record> reader = fileInfosReader(path)) {
            return reader.read() == null;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed loading files", e);
        }
    }

    @Override
    public void clearTable() {
        Path path = new Path(stateStorePath + "/files");
        try {
            path.getFileSystem(conf).delete(path, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        s3RevisionUtils.deleteFilesRevision();
    }

    private String getFilesPath(RevisionId revisionId) {
        return stateStorePath + "/files/" + revisionId.getRevision() + "-" + revisionId.getUuid() + "-files.parquet";
    }

    private Record getRecordFromFileInfo(FileInfo fileInfo) {
        Record record = new Record();
        record.put("fileName", fileInfo.getFilename());
        record.put("fileStatus", "" + fileInfo.getFileStatus());
        record.put("partitionId", fileInfo.getPartitionId());
        record.put("lastStateStoreUpdateTime", fileInfo.getLastStateStoreUpdateTime());
        record.put("numberOfRecords", fileInfo.getNumberOfRecords());
        if (null == fileInfo.getJobId()) {
            record.put("jobId", "null");
        } else {
            record.put("jobId", fileInfo.getJobId());
        }
        return record;
    }

    private FileInfo getFileInfoFromRecord(Record record) {
        String jobId = (String) record.get("jobId");
        return FileInfo.builder()
                .filename((String) record.get("fileName"))
                .fileStatus(FileInfo.FileStatus.valueOf((String) record.get("fileStatus")))
                .partitionId((String) record.get("partitionId"))
                .lastStateStoreUpdateTime((Long) record.get("lastStateStoreUpdateTime"))
                .numberOfRecords((Long) record.get("numberOfRecords"))
                .jobId("null".equals(jobId) ? null : jobId)
                .build();
    }

    private void writeFileInfosToParquet(List<FileInfo> fileInfos, String path) throws IOException {
        ParquetWriter<Record> recordWriter = ParquetRecordWriterFactory.createParquetRecordWriter(new Path(path), FILE_SCHEMA, conf);

        for (FileInfo fileInfo : fileInfos) {
            recordWriter.write(getRecordFromFileInfo(fileInfo));
        }
        recordWriter.close();
        LOGGER.debug("Wrote fileinfos to " + path);
    }

    private List<FileInfo> readFileInfosFromParquet(String path) throws IOException {
        List<FileInfo> fileInfos = new ArrayList<>();
        try (ParquetReader<Record> reader = fileInfosReader(path)) {
            ParquetReaderIterator recordReader = new ParquetReaderIterator(reader);
            while (recordReader.hasNext()) {
                fileInfos.add(getFileInfoFromRecord(recordReader.next()));
            }
        }
        return fileInfos;
    }

    private ParquetReader<Record> fileInfosReader(String path) throws IOException {
        return new ParquetRecordReader.Builder(new Path(path), FILE_SCHEMA)
                .withConf(conf)
                .build();
    }

    public void fixTime(Instant now) {
        clock = Clock.fixed(now, ZoneId.of("UTC"));
    }

    private static FileInfo setLastUpdateTime(FileInfo fileInfo, long updateTime) {
        return fileInfo.toBuilder().lastStateStoreUpdateTime(updateTime).build();
    }

    private static List<FileInfo> setLastUpdateTimes(List<FileInfo> fileInfos, long updateTime) {
        return fileInfos.stream().map(file -> setLastUpdateTime(file, updateTime)).collect(Collectors.toList());
    }

    static final class Builder {
        private String stateStorePath;
        private int garbageCollectorDelayBeforeDeletionInMinutes;
        private Configuration conf;
        private S3RevisionUtils s3RevisionUtils;

        private Builder() {
        }

        Builder stateStorePath(String stateStorePath) {
            this.stateStorePath = stateStorePath;
            return this;
        }

        Builder garbageCollectorDelayBeforeDeletionInMinutes(int garbageCollectorDelayBeforeDeletionInMinutes) {
            this.garbageCollectorDelayBeforeDeletionInMinutes = garbageCollectorDelayBeforeDeletionInMinutes;
            return this;
        }

        Builder conf(Configuration conf) {
            this.conf = conf;
            return this;
        }

        Builder s3RevisionUtils(S3RevisionUtils s3RevisionUtils) {
            this.s3RevisionUtils = s3RevisionUtils;
            return this;
        }

        S3FileInfoStore build() {
            return new S3FileInfoStore(this);
        }
    }
}
