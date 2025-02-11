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
package sleeper.statestore;

import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.statestore.StateStore;
import sleeper.core.table.TableIdentity;

import java.util.Map;
import java.util.Objects;

import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;

public class FixedStateStoreProvider extends StateStoreProvider {

    public FixedStateStoreProvider(TableProperties singleTableProperties, StateStore stateStore) {
        super(tableProperties -> {
            TableIdentity requestedId = tableProperties.getId();
            if (!Objects.equals(requestedId, singleTableProperties.getId())) {
                throw new IllegalArgumentException("Table not found: " + requestedId);
            }
            return stateStore;
        });
    }

    public FixedStateStoreProvider(Map<String, StateStore> stateStoreByTableName) {
        super(tableProperties -> {
            String tableName = tableProperties.get(TABLE_NAME);
            if (!stateStoreByTableName.containsKey(tableName)) {
                throw new IllegalArgumentException("Table not found: " + tableName);
            }
            return stateStoreByTableName.get(tableName);
        });
    }
}
