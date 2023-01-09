/*
 * Copyright 2023 Crown Copyright
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
package sleeper.ingest.job;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.localstack.LocalStackContainer;

import sleeper.configuration.jars.ObjectFactory;
import sleeper.configuration.properties.InstanceProperties;
import sleeper.configuration.properties.table.FixedTablePropertiesProvider;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TablePropertiesProvider;
import sleeper.core.CommonTestConstants;
import sleeper.core.record.Record;
import sleeper.core.record.RecordComparator;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.LongType;
import sleeper.ingest.testutils.AwsExternalResource;
import sleeper.ingest.testutils.RecordGenerator;
import sleeper.ingest.testutils.ResultVerifier;
import sleeper.io.parquet.record.ParquetRecordWriter;
import sleeper.io.parquet.record.SchemaConverter;
import sleeper.statestore.FixedStateStoreProvider;
import sleeper.statestore.StateStore;
import sleeper.statestore.StateStoreProvider;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static sleeper.configuration.properties.UserDefinedInstanceProperty.FILE_SYSTEM;
import static sleeper.configuration.properties.UserDefinedInstanceProperty.ID;
import static sleeper.configuration.properties.UserDefinedInstanceProperty.INGEST_PARTITION_FILE_WRITER_TYPE;
import static sleeper.configuration.properties.UserDefinedInstanceProperty.INGEST_RECORD_BATCH_TYPE;
import static sleeper.configuration.properties.table.TableProperty.ACTIVE_FILEINFO_TABLENAME;
import static sleeper.configuration.properties.table.TableProperty.DATA_BUCKET;
import static sleeper.configuration.properties.table.TableProperty.PARTITION_TABLENAME;
import static sleeper.configuration.properties.table.TableProperty.READY_FOR_GC_FILEINFO_TABLENAME;
import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;
import static sleeper.ingest.job.IngestJobTestData.createJobWithTableAndFiles;
import static sleeper.statestore.inmemory.StateStoreTestHelper.inMemoryStateStoreWithFixedSinglePartition;

@RunWith(Parameterized.class)
public class IngestJobRunnerIT {
    @ClassRule
    public static final AwsExternalResource AWS_EXTERNAL_RESOURCE = new AwsExternalResource(
            LocalStackContainer.Service.S3);
    private static final String TEST_INSTANCE_NAME = "myinstance";
    private static final String TEST_TABLE_NAME = "mytable";
    private static final String INGEST_DATA_BUCKET_NAME = TEST_INSTANCE_NAME + "-" + TEST_TABLE_NAME + "-ingestdata";
    private static final String TABLE_DATA_BUCKET_NAME = TEST_INSTANCE_NAME + "-" + TEST_TABLE_NAME + "-tabledata";
    private final String recordBatchType;
    private final String partitionFileWriterType;
    private final String fileSystemPrefix;
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder(CommonTestConstants.TMP_DIRECTORY);
    private String currentLocalIngestDirectory;
    private String currentLocalTableDataDirectory;

    public IngestJobRunnerIT(String recordBatchType,
                             String partitionFileWriterType,
                             String fileSystemPrefix) {
        this.recordBatchType = recordBatchType;
        this.partitionFileWriterType = partitionFileWriterType;
        this.fileSystemPrefix = fileSystemPrefix;
    }

    @Parameterized.Parameters(name = "batchType={0}, fileWriterType={1}, fileSystemPrefix=\"{2}\"")
    public static Collection<Object[]> parametersForTests() {
        return Arrays.asList(new Object[][]{
                {"arrow", "async", "s3a://"},
                {"arrow", "direct", "s3a://"},
                {"arrow", "direct", ""},
                {"arraylist", "async", "s3a://"},
                {"arraylist", "direct", "s3a://"},
                {"arraylist", "direct", ""}});
    }

    @Before
    public void before() throws IOException {
        AWS_EXTERNAL_RESOURCE.getS3Client().createBucket(TABLE_DATA_BUCKET_NAME);
        AWS_EXTERNAL_RESOURCE.getS3Client().createBucket(INGEST_DATA_BUCKET_NAME);
        currentLocalIngestDirectory = temporaryFolder.newFolder().getAbsolutePath();
        currentLocalTableDataDirectory = temporaryFolder.newFolder().getAbsolutePath();
    }

    @After
    public void after() {
        AWS_EXTERNAL_RESOURCE.clear();
    }

    private InstanceProperties getInstanceProperties() {
        InstanceProperties instanceProperties = new InstanceProperties();
        instanceProperties.set(ID, TEST_INSTANCE_NAME);
        instanceProperties.set(FILE_SYSTEM, fileSystemPrefix);
        instanceProperties.set(INGEST_RECORD_BATCH_TYPE, recordBatchType);
        instanceProperties.set(INGEST_PARTITION_FILE_WRITER_TYPE, partitionFileWriterType);
        return instanceProperties;
    }

    private TableProperties createTable(Schema schema) {
        InstanceProperties instanceProperties = getInstanceProperties();

        TableProperties tableProperties = new TableProperties(instanceProperties);
        tableProperties.set(TABLE_NAME, TEST_TABLE_NAME);
        tableProperties.setSchema(schema);
        tableProperties.set(DATA_BUCKET, getTableDataBucket());
        tableProperties.set(ACTIVE_FILEINFO_TABLENAME, TEST_TABLE_NAME + "-af");
        tableProperties.set(READY_FOR_GC_FILEINFO_TABLENAME, TEST_TABLE_NAME + "-rfgcf");
        tableProperties.set(PARTITION_TABLENAME, TEST_TABLE_NAME + "-p");

        return tableProperties;
    }

    private String getTableDataBucket() {
        String fileSystemPrefix = getInstanceProperties().get(FILE_SYSTEM).toLowerCase(Locale.ROOT);
        switch (fileSystemPrefix) {
            case "s3a://":
                return TABLE_DATA_BUCKET_NAME;
            case "":
                return currentLocalTableDataDirectory;
            default:
                throw new AssertionError(String.format("File system %s is not supported", fileSystemPrefix));
        }
    }

    private String getIngestBucket() {
        String fileSystemPrefix = getInstanceProperties().get(FILE_SYSTEM).toLowerCase(Locale.ROOT);
        switch (fileSystemPrefix) {
            case "s3a://":
                return INGEST_DATA_BUCKET_NAME;
            case "":
                return currentLocalIngestDirectory;
            default:
                throw new AssertionError(String.format("File system %s is not supported", fileSystemPrefix));
        }
    }

    private List<String> writeParquetFilesForIngest(
            RecordGenerator.RecordListAndSchema recordListAndSchema,
            String subDirectory,
            int numberOfFiles) throws IOException {
        List<String> files = new ArrayList<>();

        for (int fileNo = 0; fileNo < numberOfFiles; fileNo++) {
            String fileWithoutSystemPrefix = String.format("%s/%s/file-%d.parquet", getIngestBucket(), subDirectory, fileNo);
            files.add(fileWithoutSystemPrefix);
            ParquetWriter<Record> writer = new ParquetRecordWriter.Builder(new Path(fileSystemPrefix + fileWithoutSystemPrefix),
                    SchemaConverter.getSchema(recordListAndSchema.sleeperSchema), recordListAndSchema.sleeperSchema)
                    .withRowGroupSize(ParquetWriter.DEFAULT_BLOCK_SIZE)
                    .withPageSize(ParquetWriter.DEFAULT_PAGE_SIZE)
                    .withConf(AWS_EXTERNAL_RESOURCE.getHadoopConfiguration())
                    .build();
            for (Record record : recordListAndSchema.recordList) {
                writer.write(record);
            }
            writer.close();
        }

        return files;
    }

    private void consumeAndVerify(Schema sleeperSchema,
                                  IngestJob job,
                                  List<Record> expectedRecordList,
                                  int expectedNoOfFiles) throws Exception {
        String localDir = temporaryFolder.newFolder().getAbsolutePath();
        InstanceProperties instanceProperties = getInstanceProperties();
        TableProperties tableProperties = createTable(sleeperSchema);
        TablePropertiesProvider tablePropertiesProvider = new FixedTablePropertiesProvider(tableProperties);
        StateStore stateStore = inMemoryStateStoreWithFixedSinglePartition(sleeperSchema);
        StateStoreProvider stateStoreProvider = new FixedStateStoreProvider(tablePropertiesProvider.getTableProperties(TEST_TABLE_NAME), stateStore);

        // Run the job consumer
        IngestJobRunner ingestJobRunner = new IngestJobRunner(
                new ObjectFactory(instanceProperties, null, temporaryFolder.newFolder().getAbsolutePath()),
                instanceProperties,
                tablePropertiesProvider,
                stateStoreProvider,
                localDir,
                AWS_EXTERNAL_RESOURCE.getS3AsyncClient(),
                AWS_EXTERNAL_RESOURCE.getHadoopConfiguration());
        ingestJobRunner.ingest(job);

        // Verify the results
        ResultVerifier.verify(
                stateStore,
                sleeperSchema,
                key -> 0,
                expectedRecordList,
                Collections.singletonMap(0, expectedNoOfFiles),
                AWS_EXTERNAL_RESOURCE.getHadoopConfiguration(),
                temporaryFolder.newFolder().getAbsolutePath());
    }

    @Test
    public void shouldIngestParquetFiles() throws Exception {
        RecordGenerator.RecordListAndSchema recordListAndSchema = RecordGenerator.genericKey1D(
                new LongType(),
                LongStream.range(-5, 5).boxed().collect(Collectors.toList()));
        List<String> files = writeParquetFilesForIngest(recordListAndSchema, "", 2);
        List<Record> doubledRecords = Stream.of(recordListAndSchema.recordList, recordListAndSchema.recordList)
                .flatMap(List::stream)
                .sorted(new RecordComparator(recordListAndSchema.sleeperSchema))
                .collect(Collectors.toList());
        IngestJob ingestJob = createJobWithTableAndFiles("id", TEST_TABLE_NAME, files);
        consumeAndVerify(recordListAndSchema.sleeperSchema, ingestJob, doubledRecords, 1);
    }

    @Test
    public void shouldBeAbleToHandleAllFileFormats() throws Exception {
        RecordGenerator.RecordListAndSchema recordListAndSchema = RecordGenerator.genericKey1D(
                new LongType(),
                LongStream.range(-100, 100).boxed().collect(Collectors.toList()));
        List<String> files = writeParquetFilesForIngest(recordListAndSchema, "", 1);
        URI uri1 = new URI(fileSystemPrefix + getIngestBucket() + "/file-1.crc");
        FileSystem.get(uri1, AWS_EXTERNAL_RESOURCE.getHadoopConfiguration()).createNewFile(new Path(uri1));
        files.add(getIngestBucket() + "/file-1.crc");
        URI uri2 = new URI(fileSystemPrefix + getIngestBucket() + "/file-2.csv");
        FileSystem.get(uri2, AWS_EXTERNAL_RESOURCE.getHadoopConfiguration()).createNewFile(new Path(uri2));
        files.add(getIngestBucket() + "/file-2.csv");
        IngestJob ingestJob = IngestJob.builder()
                .tableName(TEST_TABLE_NAME).id("id").files(files)
                .build();
        consumeAndVerify(recordListAndSchema.sleeperSchema, ingestJob, recordListAndSchema.recordList, 1);
    }

    @Test
    public void shouldIngestParquetFilesInNestedDirectories() throws Exception {
        RecordGenerator.RecordListAndSchema recordListAndSchema = RecordGenerator.genericKey1D(
                new LongType(),
                LongStream.range(-5, 5).boxed().collect(Collectors.toList()));
        int noOfTopLevelDirectories = 2;
        int noOfNestings = 4;
        int noOfFilesPerDirectory = 2;
        List<String> files = IntStream.range(0, noOfTopLevelDirectories)
                .mapToObj(topLevelDirNo ->
                        IntStream.range(0, noOfNestings).mapToObj(nestingNo -> {
                            try {
                                String dirName = String.format("dir-%d%s", topLevelDirNo, String.join("", Collections.nCopies(nestingNo, "/nested-dir")));
                                return writeParquetFilesForIngest(recordListAndSchema, dirName, noOfFilesPerDirectory);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }).flatMap(List::stream).collect(Collectors.toList()))
                .flatMap(List::stream).collect(Collectors.toList());
        List<Record> expectedRecords = Stream.of(Collections.nCopies(noOfTopLevelDirectories * noOfNestings * noOfFilesPerDirectory, recordListAndSchema.recordList))
                .flatMap(List::stream)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        IngestJob ingestJob = IngestJob.builder()
                .tableName(TEST_TABLE_NAME).id("id").files(files)
                .build();
        consumeAndVerify(recordListAndSchema.sleeperSchema, ingestJob, expectedRecords, 1);
    }
}
