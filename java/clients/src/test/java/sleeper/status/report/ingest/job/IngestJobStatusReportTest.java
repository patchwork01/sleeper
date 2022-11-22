/*
 * Copyright 2022 Crown Copyright
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

package sleeper.status.report.ingest.job;

import org.junit.Test;
import sleeper.ToStringPrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.ClientTestUtils.example;

public class IngestJobStatusReportTest {
    @Test
    public void shouldReportNoIngestJobs() throws Exception {

        // When / Then
        assertThat(getStandardReport(IngestJobQuery.ALL)).hasToString(
                example("reports/ingest/job/standard/all/noJobs.txt"));
    }

    @Test
    public void shouldReportMixedIngestJobs() throws Exception {

        // When / Then
        assertThat(getStandardReport(IngestJobQuery.ALL)).hasToString(
                example("reports/ingest/job/standard/all/mixedJobs.txt"));
    }


    private String getStandardReport(IngestJobQuery query) {
        ToStringPrintStream output = new ToStringPrintStream();
        new IngestJobStatusReport(output.getPrintStream()).run(query);
        return output.toString();
    }
}
