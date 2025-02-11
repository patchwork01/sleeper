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

package sleeper.ingest.job.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import sleeper.core.record.process.status.ProcessFinishedStatus;
import sleeper.core.record.process.status.ProcessStatusUpdate;
import sleeper.ingest.job.IngestJob;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.core.record.process.RecordsProcessedSummaryTestData.summary;
import static sleeper.core.record.process.status.TestProcessStatusUpdateRecords.records;
import static sleeper.core.record.process.status.TestRunStatusUpdates.defaultUpdateTime;
import static sleeper.ingest.job.status.IngestJobStatusTestData.finishedIngestJob;
import static sleeper.ingest.job.status.IngestJobStatusTestData.singleJobStatusFrom;
import static sleeper.ingest.job.status.IngestJobStatusTestData.startedIngestJob;

public class IngestJobStatusInPeriodTest {
    private final IngestJob job = IngestJob.builder()
            .id("test-job").files("test.parquet").tableName("test-table").build();

    @Nested
    @DisplayName("Unfinished job")
    class UnfinishedJob {

        @Test
        public void shouldIncludeUnfinishedJobWhenStartedBeforeWindowStartTime() {
            // Given
            Instant jobStartTime = Instant.parse("2022-09-23T11:43:00.000Z");
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");

            // When / Then
            assertThat(unfinishedStatus(jobStartTime)
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isTrue();
        }

        @Test
        public void shouldIncludeUnfinishedJobWhenStartedDuringWindow() {
            // Given
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant jobStartTime = Instant.parse("2022-09-23T11:44:30.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");

            // When / Then
            assertThat(unfinishedStatus(jobStartTime)
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isTrue();
        }

        @Test
        public void shouldNotIncludeUnfinishedJobWhenStartedAfterWindow() {
            // Given
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");
            Instant jobStartTime = Instant.parse("2022-09-23T11:45:30.000Z");

            // When / Then
            assertThat(unfinishedStatus(jobStartTime)
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Finished job")
    class FinishedJob {

        @Test
        public void shouldIncludeFinishedJobWhenRanInsideWindow() {
            // Given
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant jobStartTime = Instant.parse("2022-09-23T11:44:15.000Z");
            Instant jobFinishTime = Instant.parse("2022-09-23T11:44:45.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");

            // When / Then
            assertThat(finishedStatus(jobStartTime, jobFinishTime)
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isTrue();
        }

        @Test
        public void shouldNotIncludeFinishedJobWhenFinishedBeforeWindow() {
            // Given
            Instant jobStartTime = Instant.parse("2022-09-23T11:42:00.000Z");
            Instant jobFinishTime = Instant.parse("2022-09-23T11:43:00.000Z");
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");

            // When / Then
            assertThat(finishedStatus(jobStartTime, jobFinishTime)
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isFalse();
        }

        @Test
        public void shouldIncludeFinishedJobWhenOverlapsWithWindow() {
            // Given
            Instant jobStartTime = Instant.parse("2022-09-23T11:43:30.000Z");
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant jobFinishTime = Instant.parse("2022-09-23T11:44:30.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");

            // When / Then
            assertThat(finishedStatus(jobStartTime, jobFinishTime)
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Job with multiple runs")
    class MultipleRunsJob {

        @Test
        public void shouldIncludeUnfinishedJobWhenOneRunFinishedBeforeWindow() {
            // Given
            Instant run1StartTime = Instant.parse("2022-09-23T11:43:15.000Z");
            Instant run1FinishTime = Instant.parse("2022-09-23T11:43:45.000Z");
            Instant run2StartTime = Instant.parse("2022-09-23T11:43:50.000Z");
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");

            // When / Then
            assertThat(statusFromUpdates(
                    startedRun(run1StartTime),
                    finishedRun(run1StartTime, run1FinishTime),
                    startedRun(run2StartTime))
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isTrue();
        }

        @Test
        public void shouldNotIncludeFinishedJobWhenTwoRunsFinishedBeforeWindow() {
            // Given
            Instant run1StartTime = Instant.parse("2022-09-23T11:43:15.000Z");
            Instant run1FinishTime = Instant.parse("2022-09-23T11:43:30.000Z");
            Instant run2StartTime = Instant.parse("2022-09-23T11:43:40.000Z");
            Instant run2FinishTime = Instant.parse("2022-09-23T11:43:55.000Z");
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");

            // When / Then
            assertThat(statusFromUpdates(
                    startedRun(run1StartTime),
                    finishedRun(run1StartTime, run1FinishTime),
                    startedRun(run2StartTime),
                    finishedRun(run2StartTime, run2FinishTime))
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isFalse();
        }

        @Test
        public void shouldIncludeFinishedJobWhenSecondRunFinishedDuringWindow() {
            // Given
            Instant run1StartTime = Instant.parse("2022-09-23T11:43:15.000Z");
            Instant run1FinishTime = Instant.parse("2022-09-23T11:43:45.000Z");
            Instant run2StartTime = Instant.parse("2022-09-23T11:43:45.000Z");
            Instant windowStartTime = Instant.parse("2022-09-23T11:44:00.000Z");
            Instant run2FinishTime = Instant.parse("2022-09-23T11:44:15.000Z");
            Instant windowEndTime = Instant.parse("2022-09-23T11:45:00.000Z");

            // When / Then
            assertThat(statusFromUpdates(
                    startedRun(run1StartTime),
                    finishedRun(run1StartTime, run1FinishTime),
                    startedRun(run2StartTime),
                    finishedRun(run2StartTime, run2FinishTime))
                    .isInPeriod(windowStartTime, windowEndTime))
                    .isTrue();
        }
    }

    private IngestJobStatus unfinishedStatus(Instant startTime) {
        return startedIngestJob(job, "test-task-id", startTime);
    }

    private IngestJobStatus finishedStatus(Instant startTime, Instant finishTime) {
        return finishedIngestJob(job, "test-task-id", summary(startTime, finishTime, 100, 100));
    }

    private IngestJobStatus statusFromUpdates(ProcessStatusUpdate... updates) {
        return singleJobStatusFrom(records().fromUpdates(updates));
    }

    private ProcessStatusUpdate startedRun(Instant startedTime) {
        return IngestJobStartedStatus.startAndUpdateTime(job, startedTime, defaultUpdateTime(startedTime));
    }

    private ProcessStatusUpdate finishedRun(Instant startedTime, Instant finishedTime) {
        return ProcessFinishedStatus.updateTimeAndSummary(defaultUpdateTime(finishedTime),
                summary(startedTime, finishedTime, 100, 100));
    }
}
