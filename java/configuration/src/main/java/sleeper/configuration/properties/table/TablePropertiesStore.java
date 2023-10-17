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

import sleeper.core.table.TableId;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface TablePropertiesStore {

    TableProperties loadProperties(TableId tableId);

    Optional<TableProperties> loadByName(String tableName);

    Optional<TableProperties> loadByNameNoValidation(String tableName);

    Stream<TableProperties> streamAllTables();

    Stream<TableId> streamAllTableIds();

    default List<String> listTableNames() {
        return streamAllTableIds().map(TableId::getTableName).collect(Collectors.toUnmodifiableList());
    }

    void save(TableProperties tableProperties);

    void deleteByName(String tableName);
}
