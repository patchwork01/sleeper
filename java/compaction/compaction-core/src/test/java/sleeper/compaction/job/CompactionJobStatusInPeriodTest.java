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

import org.junit.jupiter.api.Test;

import sleeper.compaction.job.status.CompactionJobStatus;
import sleeper.core.record.process.RecordsProcessedSummary;
import sleeper.core.record.process.RecordsProcessedSummaryTestData;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.compaction.job.CompactionJobStatusTestData.finishedCompactionRun;
import static sleeper.compaction.job.CompactionJobStatusTestData.jobCreated;
import static sleeper.compaction.job.CompactionJobStatusTestData.startedCompactionRun;
import static sleeper.core.record.process.status.TestProcessStatusUpdateRecords.DEFAULT_TASK_ID;

public class CompactionJobStatusInPeriodTest {

    private final CompactionJob job = new CompactionJobTestDataHelper().singleFileCompaction();

    private static RecordsProcessedSummary startAndFinishTime(Instant startTime, Instant finishTime) {
        return RecordsProcessedSummaryTestData.summary(startTime, finishTime, 200, 100);
    }

    @Test
    public void shouldBeInPeriodWithCreatedTime() {
        // Given
        Instant startTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant middleTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, middleTime);

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isTrue();
    }

    @Test
    public void shouldOverlapPeriodWhenCreatedBeforeAndUnstarted() {
        // Given
        Instant beforeTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant startTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, beforeTime);

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isTrue();
    }

    @Test
    public void shouldBeAfterPeriodWithCreatedTime() {
        // Given
        Instant startTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant afterTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, afterTime);

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isFalse();
    }

    @Test
    public void shouldOverlapPeriodWithStartedTime() {
        // Given
        Instant beforeTime = Instant.parse("2022-09-23T11:43:00.000Z");
        Instant startTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant middleTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, beforeTime,
                startedCompactionRun(DEFAULT_TASK_ID, middleTime));

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isTrue();
    }

    @Test
    public void shouldIncludePeriodWithStartedTime() {
        // Given
        Instant beforeTime = Instant.parse("2022-09-23T11:43:00.000Z");
        Instant startTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant afterTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, beforeTime,
                startedCompactionRun(DEFAULT_TASK_ID, afterTime));

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isTrue();
    }

    @Test
    public void shouldOverlapPeriodWhenStartedBeforeAndUnfinished() {
        // Given
        Instant beforeTime1 = Instant.parse("2022-09-23T11:43:00.000Z");
        Instant beforeTime2 = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant startTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, beforeTime1,
                startedCompactionRun(DEFAULT_TASK_ID, beforeTime2));

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isTrue();
    }

    @Test
    public void shouldBeAfterPeriodWithStartedTime() {
        // Given
        Instant startTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant afterTime1 = Instant.parse("2022-09-23T11:44:02.000Z");
        Instant afterTime2 = Instant.parse("2022-09-23T11:44:03.000Z");
        CompactionJobStatus status = jobCreated(job, afterTime1,
                startedCompactionRun(DEFAULT_TASK_ID, afterTime2));

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isFalse();
    }

    @Test
    public void shouldOverlapPeriodWithFinishedTime() {
        // Given
        Instant beforeTime1 = Instant.parse("2022-09-23T11:43:00.000Z");
        Instant beforeTime2 = Instant.parse("2022-09-23T11:43:01.000Z");
        Instant startTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant middleTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, beforeTime1,
                finishedCompactionRun(DEFAULT_TASK_ID, startAndFinishTime(beforeTime2, middleTime)));

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isTrue();
    }

    @Test
    public void shouldIncludePeriodWithFinishedUpdateTime() {
        // Given
        Instant beforeTime1 = Instant.parse("2022-09-23T11:43:00.000Z");
        Instant beforeTime2 = Instant.parse("2022-09-23T11:43:01.000Z");
        Instant startTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant afterTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, beforeTime1,
                finishedCompactionRun(DEFAULT_TASK_ID, startAndFinishTime(beforeTime2, afterTime)));

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isTrue();
    }

    @Test
    public void shouldBeBeforePeriodWithFinishedUpdateTime() {
        // Given
        Instant beforeTime1 = Instant.parse("2022-09-23T11:43:00.000Z");
        Instant beforeTime2 = Instant.parse("2022-09-23T11:43:01.000Z");
        Instant beforeTime3 = Instant.parse("2022-09-23T11:43:31.000Z");
        Instant startTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:02.000Z");
        CompactionJobStatus status = jobCreated(job, beforeTime1,
                finishedCompactionRun(DEFAULT_TASK_ID, startAndFinishTime(beforeTime2, beforeTime3)));

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isFalse();
    }

    @Test
    public void shouldBeAfterPeriodWithFinishedUpdateTime() {
        // Given
        Instant startTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant endTime = Instant.parse("2022-09-23T11:44:01.000Z");
        Instant afterTime1 = Instant.parse("2022-09-23T11:44:02.000Z");
        Instant afterTime2 = Instant.parse("2022-09-23T11:44:03.000Z");
        Instant afterTime3 = Instant.parse("2022-09-23T11:44:33.000Z");
        CompactionJobStatus status = jobCreated(job, afterTime1,
                finishedCompactionRun(DEFAULT_TASK_ID, startAndFinishTime(afterTime2, afterTime3)));

        // When / Then
        assertThat(status.isInPeriod(startTime, endTime)).isFalse();
    }

    @Test
    public void shouldBeInPeriodWhenLastUpdateStartedAfterLastFinishTime() {
        // Given
        Instant createTime = Instant.parse("2022-09-23T11:44:00.000Z");
        Instant run1StartTime = Instant.parse("2022-09-23T11:45:00.000Z");
        Instant run1FinishTime = Instant.parse("2022-09-23T11:45:30.000Z");
        Instant periodStart = Instant.parse("2022-09-23T11:45:45.000Z");
        Instant run2StartTime = Instant.parse("2022-09-23T11:46:00.000Z");
        Instant periodEnd = Instant.parse("2022-09-23T12:00:00.000Z");
        CompactionJobStatus status = jobCreated(job, createTime,
                startedCompactionRun(DEFAULT_TASK_ID, run2StartTime),
                finishedCompactionRun(DEFAULT_TASK_ID, startAndFinishTime(run1StartTime, run1FinishTime)));

        // When / Then
        assertThat(status.isInPeriod(periodStart, periodEnd)).isTrue();
    }
}
