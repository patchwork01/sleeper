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
package sleeper.ingest.batcher;

import org.junit.jupiter.api.BeforeEach;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.FixedTablePropertiesProvider;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.validation.BatchIngestMode;
import sleeper.ingest.batcher.testutil.FileIngestRequestTestHelper;
import sleeper.ingest.batcher.testutil.InMemoryIngestBatcherStore;
import sleeper.ingest.batcher.testutil.IngestBatcherQueuesInMemory;
import sleeper.ingest.job.IngestJob;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static sleeper.configuration.properties.InstancePropertiesTestHelper.createTestInstanceProperties;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.INGEST_JOB_QUEUE_URL;
import static sleeper.configuration.properties.table.TablePropertiesTestHelper.createTestTableProperties;
import static sleeper.configuration.properties.table.TableProperty.INGEST_BATCHER_INGEST_MODE;
import static sleeper.configuration.properties.table.TableProperty.INGEST_BATCHER_MIN_JOB_FILES;
import static sleeper.configuration.properties.table.TableProperty.INGEST_BATCHER_MIN_JOB_SIZE;
import static sleeper.configuration.properties.table.TableProperty.TABLE_ID;
import static sleeper.core.schema.SchemaTestHelper.schemaWithKey;
import static sleeper.ingest.batcher.testutil.FileIngestRequestTestHelper.DEFAULT_TABLE_ID;
import static sleeper.ingest.batcher.testutil.FileIngestRequestTestHelper.FIRST_REQUEST_TIME;
import static sleeper.ingest.batcher.testutil.IngestBatcherTestHelper.jobIdSupplier;
import static sleeper.ingest.batcher.testutil.IngestBatcherTestHelper.timeSupplier;

public class IngestBatcherTestBase {
    protected final InstanceProperties instanceProperties = createTestInstanceProperties();
    protected final TableProperties tableProperties = createTableProperties(DEFAULT_TABLE_ID);
    protected final IngestBatcherStore store = new InMemoryIngestBatcherStore();
    protected final IngestBatcherQueuesInMemory queues = new IngestBatcherQueuesInMemory();
    private final FileIngestRequestTestHelper requests = new FileIngestRequestTestHelper();

    @BeforeEach
    void setUp() {
        instanceProperties.set(INGEST_JOB_QUEUE_URL, "test-ingest-queue-url");
    }

    protected Map<String, List<Object>> queueMessages(IngestJob... jobs) {
        return Map.of("test-ingest-queue-url", List.of(jobs));
    }

    protected TableProperties createTableProperties(String tableId) {
        TableProperties properties = createTestTableProperties(instanceProperties, schemaWithKey("key"));
        properties.set(INGEST_BATCHER_INGEST_MODE, BatchIngestMode.STANDARD_INGEST.toString());
        properties.set(INGEST_BATCHER_MIN_JOB_SIZE, "0");
        properties.set(INGEST_BATCHER_MIN_JOB_FILES, "1");
        properties.set(TABLE_ID, tableId);
        return properties;
    }

    protected IngestJob jobWithFiles(String jobId, String... files) {
        return IngestJob.builder()
                .files(files)
                .tableId(DEFAULT_TABLE_ID)
                .id(jobId)
                .build();
    }

    protected FileIngestRequest addFileToStore(String file) {
        return addFileToStore(ingestRequest()
                .file(file).build());
    }

    protected FileIngestRequest.Builder ingestRequest() {
        return requests.fileRequest();
    }

    protected FileIngestRequest addFileToStore(FileIngestRequest request) {
        store.addFile(request);
        return request;
    }

    protected FileIngestRequest addFileToStore(Consumer<FileIngestRequest.Builder> config) {
        FileIngestRequest.Builder builder = ingestRequest();
        config.accept(builder);
        return addFileToStore(builder.build());
    }

    protected void batchFilesWithJobIds(String... jobIds) {
        batchFilesWithJobIds(List.of(jobIds), builder -> {
        });
    }

    protected void batchFilesWithTablesAndJobIds(List<TableProperties> tables, List<String> jobIds) {
        batchFilesWithJobIds(jobIds, builder ->
                builder.tablePropertiesProvider(new FixedTablePropertiesProvider(tables)));
    }

    protected void batchFilesWithJobIds(List<String> jobIds, Consumer<IngestBatcher.Builder> config) {
        IngestBatcher.Builder builder = IngestBatcher.builder()
                .instanceProperties(instanceProperties)
                .tablePropertiesProvider(new FixedTablePropertiesProvider(tableProperties))
                .jobIdSupplier(jobIdSupplier(jobIds))
                .timeSupplier(timeSupplier(FIRST_REQUEST_TIME.plus(Duration.ofSeconds(20))))
                .store(store).queueClient(queues);
        config.accept(builder);
        builder.build().batchFiles();
    }
}
