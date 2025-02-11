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
package sleeper.compaction.status.store.job;

import org.junit.jupiter.api.Test;

import sleeper.compaction.job.CompactionJob;
import sleeper.compaction.job.CompactionJobStatusTestData;
import sleeper.compaction.status.store.testutils.DynamoDBCompactionJobStatusStoreTestBase;
import sleeper.core.partition.Partition;
import sleeper.core.statestore.FileInfoFactory;

import java.time.Instant;
import java.time.Period;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryCompactionJobStatusByPeriodIT extends DynamoDBCompactionJobStatusStoreTestBase {

    @Test
    public void shouldReturnCompactionJobsInPeriod() {
        // Given
        Partition partition = singlePartition();
        FileInfoFactory fileFactory = fileFactory(partition);
        CompactionJob job1 = jobFactory.createCompactionJob(
                Collections.singletonList(fileFactory.leafFile("file1", 123L, "a", "c")),
                partition.getId());
        CompactionJob job2 = jobFactory.createCompactionJob(
                Collections.singletonList(fileFactory.leafFile("file2", 456L, "d", "f")),
                partition.getId());

        // When
        store.jobCreated(job1);
        store.jobCreated(job2);

        // Then
        Instant epochStart = Instant.ofEpochMilli(0);
        Instant farFuture = epochStart.plus(Period.ofDays(999999999));
        assertThat(store.getJobsInTimePeriod(tableId, epochStart, farFuture))
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(
                        CompactionJobStatusTestData.jobCreated(job2, ignoredUpdateTime()),
                        CompactionJobStatusTestData.jobCreated(job1, ignoredUpdateTime()));
    }

    @Test
    public void shouldExcludeCompactionJobOutsidePeriod() {
        // Given
        Partition partition = singlePartition();
        FileInfoFactory fileFactory = fileFactory(partition);
        CompactionJob job = jobFactory.createCompactionJob(
                Collections.singletonList(fileFactory.leafFile(123L, "a", "z")),
                partition.getId());

        // When
        store.jobCreated(job);

        // Then
        Instant periodStart = Instant.now().minus(Period.ofDays(2));
        Instant periodEnd = periodStart.plus(Period.ofDays(1));
        assertThat(store.getJobsInTimePeriod(tableId, periodStart, periodEnd)).isEmpty();
    }

    @Test
    public void shouldExcludeCompactionJobInOtherTable() {
        // Given
        Partition partition = singlePartition();
        FileInfoFactory fileFactory = fileFactory(partition);
        CompactionJob job1 = jobFactory.createCompactionJob(
                Collections.singletonList(fileFactory.leafFile("file1", 123L, "a", "c")),
                partition.getId());
        CompactionJob job2 = jobFactoryForOtherTable().createCompactionJob(
                Collections.singletonList(fileFactory.leafFile("file2", 456L, "d", "f")),
                partition.getId());

        // When
        store.jobCreated(job1);
        store.jobCreated(job2);

        // Then
        Instant epochStart = Instant.ofEpochMilli(0);
        Instant farFuture = epochStart.plus(Period.ofDays(999999999));
        assertThat(store.getJobsInTimePeriod(tableId, epochStart, farFuture))
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(CompactionJobStatusTestData.jobCreated(job1, ignoredUpdateTime()));
    }

    @Test
    public void shouldIncludeFinishedStatusUpdateOutsidePeriod() throws Exception {
        // Given
        Partition partition = singlePartition();
        FileInfoFactory fileFactory = fileFactory(partition);
        CompactionJob job = jobFactory.createCompactionJob(
                Collections.singletonList(fileFactory.leafFile(123L, "a", "z")),
                partition.getId());

        // When
        Instant periodStart = Instant.now().minus(Period.ofDays(1));
        store.jobCreated(job);
        store.jobStarted(job, defaultStartTime(), DEFAULT_TASK_ID);
        Thread.sleep(1);
        Instant periodEnd = Instant.now();
        Thread.sleep(1);
        store.jobFinished(job, defaultSummary(), DEFAULT_TASK_ID);

        // Then
        assertThat(store.getJobsInTimePeriod(tableId, periodStart, periodEnd))
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(finishedStatusWithDefaults(job));
    }

    @Test
    public void shouldIncludeJobCreatedOutsidePeriod() throws Exception {
        // Given
        Partition partition = singlePartition();
        FileInfoFactory fileFactory = fileFactory(partition);
        CompactionJob job = jobFactory.createCompactionJob(
                Collections.singletonList(fileFactory.leafFile(123L, "a", "z")),
                partition.getId());

        // When
        store.jobCreated(job);
        Thread.sleep(1);
        Instant periodStart = Instant.now();
        Thread.sleep(1);
        store.jobStarted(job, defaultStartTime(), DEFAULT_TASK_ID);
        store.jobFinished(job, defaultSummary(), DEFAULT_TASK_ID);
        Instant periodEnd = periodStart.plus(Period.ofDays(1));

        // Then
        assertThat(store.getJobsInTimePeriod(tableId, periodStart, periodEnd))
                .usingRecursiveFieldByFieldElementComparator(IGNORE_UPDATE_TIMES)
                .containsExactly(finishedStatusWithDefaults(job));
    }
}
