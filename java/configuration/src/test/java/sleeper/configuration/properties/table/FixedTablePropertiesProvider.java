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
package sleeper.configuration.properties.table;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class FixedTablePropertiesProvider extends TablePropertiesProvider {
    public FixedTablePropertiesProvider(TableProperties tableProperties) {
        this(List.of(tableProperties));
    }

    public FixedTablePropertiesProvider(List<TableProperties> tables) {
        super(InMemoryTableProperties.getStoreReturningExactInstances(tables),
                Duration.ofMinutes(Integer.MAX_VALUE), () -> Instant.MIN);
    }
}
