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

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.compaction.job.CompactionJobStatusTestData.finishedCompactionRun;

public class QueryCompactionJobStatusByIdIT extends DynamoDBCompactionJobStatusStoreTestBase {

    @Test
    public void shouldReturnCompactionJobById() {
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
        assertThat(getJobStatus(job1.getId()))
                .usingRecursiveComparison(IGNORE_UPDATE_TIMES)
                .isEqualTo(CompactionJobStatusTestData.jobCreated(job1, ignoredUpdateTime()));
    }

    @Test
    public void shouldReturnFinishedCompactionJobById() {
        // Given
        Partition partition = singlePartition();
        FileInfoFactory fileFactory = fileFactory(partition);
        CompactionJob job = jobFactory.createCompactionJob(
                Collections.singletonList(fileFactory.leafFile("file", 123L, "a", "c")),
                partition.getId());

        // When
        store.jobCreated(job);
        store.jobStarted(job, defaultStartTime(), "test-task");
        store.jobFinished(job, defaultSummary(), "test-task");

        // Then
        assertThat(getJobStatus(job.getId()))
                .usingRecursiveComparison(IGNORE_UPDATE_TIMES)
                .isEqualTo(CompactionJobStatusTestData.jobCreated(job, ignoredUpdateTime(),
                        finishedCompactionRun("test-task", defaultSummary())));
    }

    @Test
    public void shouldReturnNoCompactionJobById() {
        // When / Then
        assertThat(store.getJob("not-present")).isNotPresent();
    }
}
