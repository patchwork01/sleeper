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

package sleeper.ingest;

import org.junit.Test;

import sleeper.statestore.StateStore;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.ingest.testutils.IngestRecordsTestDataHelper.getRecords;
import static sleeper.statestore.inmemory.StateStoreTestHelper.inMemoryStateStoreWithFixedSinglePartition;

public class IngestResultTest extends IngestRecordsTestBase {
    @Test
    public void shouldReturnNumberOfRecordsFromIngestResult() throws Exception {
        // Given
        StateStore stateStore = inMemoryStateStoreWithFixedSinglePartition(schema);

        // When
        IngestResult result = ingestRecords(schema, stateStore, getRecords());

        // Then
        assertThat(result.getRecordsWritten())
                .isEqualTo(2L);
    }

    @Test
    public void shouldReturnFileInfoListFromIngestResult() throws Exception {
        // Given
        StateStore stateStore = inMemoryStateStoreWithFixedSinglePartition(schema);

        // When
        IngestResult result = ingestRecords(schema, stateStore, getRecords());

        // Then
        assertThat(result.getFileInfoList())
                .containsExactlyInAnyOrderElementsOf(stateStore.getActiveFiles());
    }
}
