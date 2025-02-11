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
package sleeper.query.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TablePropertiesProvider;
import sleeper.core.schema.Schema;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Serialises a {@link Query} to and from JSON.
 */
public class QuerySerDe {
    private final Gson gson;
    private final Gson gsonPrettyPrinting;
    private final SchemaLoader schemaLoader;

    private QuerySerDe(SchemaLoader schemaLoader) {
        GsonBuilder builder = new GsonBuilder()
                .serializeNulls();
        gson = builder.create();
        gsonPrettyPrinting = builder.setPrettyPrinting().create();
        this.schemaLoader = schemaLoader;
    }

    public QuerySerDe(TablePropertiesProvider tablePropertiesProvider) {
        this(new SchemaLoaderFromTableProvider(tablePropertiesProvider));
    }

    public QuerySerDe(Schema schema) {
        this(new FixedSchemaLoader(schema));
    }

    public String toJson(Query query) {
        return gson.toJson(QueryJson.from(query, schemaLoader));
    }

    public String toJson(LeafPartitionQuery leafQuery) {
        return gson.toJson(QueryJson.from(leafQuery, schemaLoader));
    }

    public String toJson(Query query, boolean prettyPrint) {
        if (prettyPrint) {
            return gsonPrettyPrinting.toJson(QueryJson.from(query, schemaLoader));
        }
        return toJson(query);
    }

    public String toJson(LeafPartitionQuery leafQuery, boolean prettyPrint) {
        if (prettyPrint) {
            return gsonPrettyPrinting.toJson(QueryJson.from(leafQuery, schemaLoader));
        }
        return toJson(leafQuery);
    }

    public Query fromJson(String json) {
        QueryJson queryJson = gson.fromJson(json, QueryJson.class);
        return queryJson.toParentQuery(schemaLoader);
    }

    public QueryOrLeafPartitionQuery fromJsonOrLeafQuery(String json) {
        QueryJson queryJson = gson.fromJson(json, QueryJson.class);
        return queryJson.toQueryOrLeafQuery(schemaLoader);
    }

    interface SchemaLoader {
        Optional<Schema> getSchemaByTableName(String tableName);

        Optional<Schema> getSchemaByTableId(String tableId);
    }

    private static class SchemaLoaderFromTableProvider implements SchemaLoader {

        private final TablePropertiesProvider provider;

        SchemaLoaderFromTableProvider(TablePropertiesProvider provider) {
            this.provider = provider;
        }

        @Override
        public Optional<Schema> getSchemaByTableName(String tableName) {
            return getSchema(() -> provider.getByName(tableName));
        }

        @Override
        public Optional<Schema> getSchemaByTableId(String tableId) {
            return getSchema(() -> provider.getById(tableId));
        }

        private Optional<Schema> getSchema(Supplier<TableProperties> getProperties) {
            try {
                return Optional.of(getProperties.get())
                        .map(TableProperties::getSchema);
            } catch (TablePropertiesProvider.TableNotFoundException e) {
                return Optional.empty();
            }
        }
    }

    private static class FixedSchemaLoader implements SchemaLoader {
        private final Schema schema;

        FixedSchemaLoader(Schema schema) {
            this.schema = schema;
        }

        @Override
        public Optional<Schema> getSchemaByTableName(String tableName) {
            return Optional.of(schema);
        }

        @Override
        public Optional<Schema> getSchemaByTableId(String tableId) {
            return Optional.of(schema);
        }
    }
}
