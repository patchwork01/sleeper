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
package sleeper.compaction.strategy.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.compaction.job.CompactionJob;
import sleeper.compaction.job.CompactionJobFactory;
import sleeper.compaction.strategy.LeafPartitionCompactionStrategy;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.partition.Partition;
import sleeper.core.statestore.FileInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static sleeper.compaction.strategy.impl.CompactionUtils.getFilesInAscendingOrder;
import static sleeper.configuration.properties.table.TableProperty.COMPACTION_FILES_BATCH_SIZE;
import static sleeper.configuration.properties.table.TableProperty.SIZE_RATIO_COMPACTION_STRATEGY_RATIO;
import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;

public class SizeRatioLeafStrategy implements LeafPartitionCompactionStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SizeRatioLeafStrategy.class);

    private String tableName;
    private int ratio;
    private int compactionFilesBatchSize;
    private CompactionJobFactory factory;

    @Override
    public void init(InstanceProperties instanceProperties, TableProperties tableProperties, CompactionJobFactory factory) {
        tableName = tableProperties.get(TABLE_NAME);
        ratio = tableProperties.getInt(SIZE_RATIO_COMPACTION_STRATEGY_RATIO);
        compactionFilesBatchSize = tableProperties.getInt(COMPACTION_FILES_BATCH_SIZE);
        this.factory = factory;
    }

    @Override
    public List<CompactionJob> createJobsForLeafPartition(Partition partition, List<FileInfo> fileInfos) {
        // Find files that meet criteria, i.e. sum of file sizes excluding largest
        // is >= ratio * largest file size.
        List<FileInfo> filesThatMeetCriteria = getListOfFilesThatMeetsCriteria(partition, fileInfos);
        if (null == filesThatMeetCriteria || filesThatMeetCriteria.isEmpty()) {
            LOGGER.info("For partition {} there is no list of files that meet the criteria", partition.getId());
            return Collections.EMPTY_LIST;
        }
        LOGGER.info("For partition {} there is a list of {} files that meet the criteria", partition.getId(), filesThatMeetCriteria.size());

        // Iterate through these files, batching into groups of compactionFilesBatchSize
        // and creating a job for each group as long as it meets the criteria.
        List<CompactionJob> compactionJobs = new ArrayList<>();
        if (filesThatMeetCriteria.size() <= compactionFilesBatchSize) {
            compactionJobs.add(factory.createCompactionJob(filesThatMeetCriteria, partition.getId()));
        } else {
            int position = 0;
            List<FileInfo> files = new ArrayList<>(filesThatMeetCriteria);
            while (position < files.size()) {
                List<FileInfo> filesForJob = new ArrayList<>();
                int j;
                for (j = 0; j < compactionFilesBatchSize && position + j < files.size(); j++) {
                    filesForJob.add(files.get(position + j));
                }
                // Create job for these files if they meet criteria
                List<Long> fileSizes = filesForJob.stream().map(FileInfo::getNumberOfRecords).collect(Collectors.toList());
                if (meetsCriteria(fileSizes)) {
                    LOGGER.info("Creating a job to compact {} files in partition {}",
                            filesForJob.size(), partition.getId());
                    compactionJobs.add(factory.createCompactionJob(filesForJob, partition.getId()));
                    filesForJob.clear();
                    position += j;
                } else {
                    position++;
                }
            }
        }

        return compactionJobs;
    }

    private List<FileInfo> getListOfFilesThatMeetsCriteria(Partition partition, List<FileInfo> fileInfos) {
        List<FileInfo> filesInAscendingOrder = getFilesInAscendingOrder(tableName, partition, fileInfos);

        while (filesInAscendingOrder.size() > 1) {
            List<Long> fileSizes = filesInAscendingOrder.stream().map(FileInfo::getNumberOfRecords).collect(Collectors.toList());
            if (meetsCriteria(fileSizes)) {
                return filesInAscendingOrder;
            } else {
                filesInAscendingOrder.remove(filesInAscendingOrder.size() - 1);
            }
        }
        return null;
    }

    private boolean meetsCriteria(List<Long> fileSizesInAscendingOrder) {
        if (fileSizesInAscendingOrder.isEmpty() || 1 == fileSizesInAscendingOrder.size()) {
            return false;
        }
        long largestFileSize = fileSizesInAscendingOrder.get(fileSizesInAscendingOrder.size() - 1);
        LOGGER.info("Largest file size is {}", largestFileSize);
        long sumOfOtherFileSizes = 0L;
        for (int i = 0; i < fileSizesInAscendingOrder.size() - 1; i++) {
            sumOfOtherFileSizes += fileSizesInAscendingOrder.get(i);
        }
        LOGGER.info("Sum of other file sizes is {}", sumOfOtherFileSizes);
        LOGGER.info("Ratio * largestFileSize <= sumOfOtherFileSizes {}", (ratio * largestFileSize <= sumOfOtherFileSizes));
        return ratio * largestFileSize <= sumOfOtherFileSizes;
    }
}
