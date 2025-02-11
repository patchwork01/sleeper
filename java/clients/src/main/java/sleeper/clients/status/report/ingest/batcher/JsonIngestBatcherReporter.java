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

package sleeper.clients.status.report.ingest.batcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;

import sleeper.clients.util.GsonConfig;
import sleeper.core.table.TableIdentity;
import sleeper.core.table.TableIdentityProvider;
import sleeper.ingest.batcher.FileIngestRequest;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

public class JsonIngestBatcherReporter implements IngestBatcherReporter {
    private final PrintStream out;

    public JsonIngestBatcherReporter() {
        this(System.out);
    }

    public JsonIngestBatcherReporter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void report(List<FileIngestRequest> fileList, BatcherQuery.Type queryType, TableIdentityProvider tableIdentityProvider) {
        Gson gson = createGson(tableIdentityProvider);
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("fileList", gson.toJsonTree(fileList));
        out.println(gson.toJson(jsonObject));
    }

    private static Gson createGson(TableIdentityProvider tableIdentityProvider) {
        return GsonConfig.standardBuilder()
                .registerTypeAdapter(FileIngestRequest.class, fileSerializer(tableIdentityProvider))
                .create();
    }

    private static JsonSerializer<FileIngestRequest> fileSerializer(TableIdentityProvider tableIdentityProvider) {
        return (request, type, context) -> {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("file", request.getFile());
            jsonObject.addProperty("fileSizeBytes", request.getFileSizeBytes());
            Optional<TableIdentity> tableIdentity = tableIdentityProvider.getById(request.getTableId());
            if (tableIdentity.isPresent()) {
                jsonObject.addProperty("tableName", tableIdentity.get().getTableName());
            } else {
                jsonObject.addProperty("tableId", request.getTableId());
                jsonObject.addProperty("tableExists", false);
            }
            jsonObject.add("receivedTime", context.serialize(request.getReceivedTime()));
            jsonObject.addProperty("jobId", request.getJobId());
            return jsonObject;
        };
    }
}
