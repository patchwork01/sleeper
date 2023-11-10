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

package sleeper.systemtest.suite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import sleeper.core.schema.Schema;
import sleeper.systemtest.suite.dsl.SleeperSystemTest;
import sleeper.systemtest.suite.fixtures.SystemTestSchema;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.systemtest.suite.fixtures.SystemTestInstance.MAIN;

@Tag("SystemTest")
public class MultipleTablesIT {
    private final SleeperSystemTest sleeper = SleeperSystemTest.getInstance();
    private final Schema schema = SystemTestSchema.DEFAULT_SCHEMA;

    @BeforeEach
    void setUp() {
        sleeper.connectToInstanceNoTables(MAIN);
    }

    @Test
    void shouldCreate200Tables() {
        sleeper.tables().createMany(200, schema);

        assertThat(sleeper.tables().loadIdentities())
                .hasSize(200);
    }

    @Test
    void shouldIngestOneFileTo200Tables() throws Exception {
        // Given we have 200 tables
        // And we have one source file to be ingested
        sleeper.tables().createMany(200, schema);
        sleeper.sourceFiles()
                .createWithNumberedRecords(schema, "file.parquet", LongStream.range(0, 100));

        // When we send an ingest job with the source file to all 200 tables
        sleeper.ingest().byQueue().sendSourceFilesToAllTables("file.parquet")
                .invokeTask().waitForJobs();

        // Then all 200 tables should contain the source file records
        // And all 200 tables should have one active file
        assertThat(sleeper.query().byQueue().allRecordsByTable())
                .hasSize(200)
                .allSatisfy(((tableIdentity, records) ->
                        assertThat(records).isEqualTo(sleeper.generateNumberedRecords(LongStream.range(0, 100)))));
        assertThat(sleeper.tableFiles().activeByTable())
                .hasSize(200)
                .allSatisfy((tableIdentity, files) ->
                        assertThat(files).hasSize(1));
    }
}
