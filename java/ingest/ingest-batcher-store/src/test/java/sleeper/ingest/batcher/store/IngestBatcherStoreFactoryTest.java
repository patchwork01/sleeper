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

package sleeper.ingest.batcher.store;

import org.junit.jupiter.api.Test;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.FixedTablePropertiesProvider;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TablePropertiesProvider;
import sleeper.ingest.batcher.IngestBatcherStore;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.configuration.properties.InstancePropertiesTestHelper.createTestInstanceProperties;
import static sleeper.configuration.properties.instance.CommonProperty.OPTIONAL_STACKS;
import static sleeper.configuration.properties.table.TablePropertiesTestHelper.createTestTableProperties;
import static sleeper.core.schema.SchemaTestHelper.schemaWithKey;

public class IngestBatcherStoreFactoryTest {
    @Test
    void shouldGetIngestBatcherStoreWhenOptionalStackEnabled() {
        // Given
        InstanceProperties properties = createTestInstanceProperties();
        properties.set(OPTIONAL_STACKS, "IngestBatcherStack");

        // When/Then
        assertThat(getStore(properties))
                .isPresent();
    }

    @Test
    void shouldNotGetIngestBatcherStoreWhenOptionalStackDisabled() {
        // Given
        InstanceProperties properties = createTestInstanceProperties();
        properties.set(OPTIONAL_STACKS, "CompactionStack");

        // When/Then
        assertThat(getStore(properties))
                .isNotPresent();
    }

    private static Optional<IngestBatcherStore> getStore(InstanceProperties properties) {
        return IngestBatcherStoreFactory.getStore(null,
                properties, createTablePropertiesProvider(properties));
    }

    private static TablePropertiesProvider createTablePropertiesProvider(InstanceProperties properties) {
        TableProperties tableProperties = createTestTableProperties(properties, schemaWithKey("key"));
        return new FixedTablePropertiesProvider(tableProperties);
    }
}
