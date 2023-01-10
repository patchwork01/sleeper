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
package sleeper.compaction.job;

import org.junit.Test;

import sleeper.compaction.job.status.CompactionJobCreatedStatus;
import sleeper.compaction.job.status.CompactionJobStartedStatus;
import sleeper.compaction.job.status.CompactionJobStatus;
import sleeper.core.record.process.status.ProcessFinishedStatus;
import sleeper.core.record.process.status.ProcessRun;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.compaction.job.CompactionJobStatusTestData.jobStatusListFromUpdates;
import static sleeper.compaction.job.CompactionJobStatusTestData.startedCompactionStatus;
import static sleeper.core.record.process.status.TestProcessStatusUpdateRecords.DEFAULT_EXPIRY;
import static sleeper.core.record.process.status.TestProcessStatusUpdateRecords.DEFAULT_TASK_ID;
import static sleeper.core.record.process.status.TestProcessStatusUpdateRecords.forJob;
import static sleeper.core.record.process.status.TestRunStatusUpdates.finishedStatus;

public class CompactionJobStatusFromRecordsTest {

    @Test
    public void shouldBuildCompactionJobStatusFromIndividualUpdates() {
        // Given
        CompactionJobCreatedStatus created1 = CompactionJobCreatedStatus.builder()
                .updateTime(Instant.parse("2022-09-23T09:23:00.012Z"))
                .partitionId("partition1").childPartitionIds(null)
                .inputFilesCount(11)
                .build();
        CompactionJobStartedStatus started1 = startedCompactionStatus(Instant.parse("2022-09-23T09:23:30.001Z"));
        ProcessFinishedStatus finished1 = finishedStatus(started1, Duration.ofSeconds(30), 200L, 100L);
        CompactionJobCreatedStatus created2 = CompactionJobCreatedStatus.builder()
                .updateTime(Instant.parse("2022-09-24T09:23:00.012Z"))
                .partitionId("partition2").childPartitionIds(Arrays.asList("A", "B"))
                .inputFilesCount(12)
                .build();
        CompactionJobStartedStatus started2 = startedCompactionStatus(Instant.parse("2022-09-24T09:23:30.001Z"));
        ProcessFinishedStatus finished2 = finishedStatus(started2, Duration.ofSeconds(30), 450L, 300L);

        // When
        List<CompactionJobStatus> statuses = jobStatusListFromUpdates(
                forJob("job1", created1, started1, finished1),
                forJob("job2", created2, started2, finished2));

        // Then
        assertThat(statuses).containsExactly(
                CompactionJobStatus.builder().jobId("job2").createdStatus(created2)
                        .singleJobRun(ProcessRun.finished(DEFAULT_TASK_ID, started2, finished2))
                        .expiryDate(DEFAULT_EXPIRY).build(),
                CompactionJobStatus.builder().jobId("job1").createdStatus(created1)
                        .singleJobRun(ProcessRun.finished(DEFAULT_TASK_ID, started1, finished1))
                        .expiryDate(DEFAULT_EXPIRY).build());
    }

    @Test
    public void shouldIgnoreJobWithNoCreatedUpdate() {
        // Given
        CompactionJobStartedStatus started = startedCompactionStatus(Instant.parse("2022-09-23T09:23:30.001Z"));
        ProcessFinishedStatus finished = finishedStatus(started, Duration.ofSeconds(30), 200L, 100L);

        // When
        List<CompactionJobStatus> statuses = jobStatusListFromUpdates(
                forJob("test-job", started, finished));

        // Then
        assertThat(statuses).isEmpty();
    }
}
