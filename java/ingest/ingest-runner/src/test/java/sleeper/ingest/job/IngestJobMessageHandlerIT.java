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

package sleeper.ingest.job;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.core.CommonTestConstants;
import sleeper.core.table.InMemoryTableIndex;
import sleeper.core.table.TableIdentity;
import sleeper.core.table.TableIndex;
import sleeper.ingest.job.status.IngestJobStatusStore;
import sleeper.ingest.job.status.WriteToMemoryIngestJobStatusStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.configuration.testutils.LocalStackAwsV1ClientHelper.buildAwsV1Client;
import static sleeper.ingest.job.status.IngestJobStatusTestData.jobStatus;
import static sleeper.ingest.job.status.IngestJobStatusTestData.rejectedRun;

@Testcontainers
public class IngestJobMessageHandlerIT {
    private static final String TEST_TABLE = "test-table";
    private static final String TEST_TABLE_ID = "test-table-id";
    private static final String TEST_BUCKET = "test-bucket";
    @Container
    public static LocalStackContainer localStackContainer = new LocalStackContainer(DockerImageName.parse(CommonTestConstants.LOCALSTACK_DOCKER_IMAGE))
            .withServices(LocalStackContainer.Service.S3);
    private final AmazonS3 s3Client = createS3Client();
    private final InstanceProperties properties = new InstanceProperties();
    private final Instant validationTime = Instant.parse("2023-10-17T14:15:00Z");
    private final TableIndex tableIndex = new InMemoryTableIndex();
    private final IngestJobStatusStore ingestJobStatusStore = new WriteToMemoryIngestJobStatusStore();
    private final IngestJobMessageHandler<IngestJob> ingestJobMessageHandler = IngestJobQueueConsumer.messageHandler(
                    properties, createHadoopConfiguration(), tableIndex, ingestJobStatusStore)
            .jobIdSupplier(() -> "job-id")
            .timeSupplier(() -> validationTime)
            .build();

    private AmazonS3 createS3Client() {
        return buildAwsV1Client(localStackContainer, LocalStackContainer.Service.S3, AmazonS3ClientBuilder.standard());
    }

    @BeforeEach
    void setup() {
        s3Client.createBucket(TEST_BUCKET);
        tableIndex.create(TableIdentity.uniqueIdAndName(TEST_TABLE_ID, TEST_TABLE));
    }

    @AfterEach
    void tearDown() {
        s3Client.listObjects(TEST_BUCKET).getObjectSummaries().forEach(s3ObjectSummary ->
                s3Client.deleteObject(TEST_BUCKET, s3ObjectSummary.getKey()));
        s3Client.deleteBucket(TEST_BUCKET);
    }

    @Nested
    @DisplayName("Expand directories")
    class ExpandDirectories {
        @Test
        void shouldExpandDirectoryWithOneFileInside() {
            // Given
            uploadFileToS3("test-dir/test-1.parquet");
            String json = "{" +
                    "\"id\":\"id\"," +
                    "\"tableName\":\"test-table\"," +
                    "\"files\":[" +
                    "   \"test-bucket/test-dir\"" +
                    "]}";

            // When
            Optional<IngestJob> job = ingestJobMessageHandler.deserialiseAndValidate(json);

            // Then
            assertThat(job).contains(jobWithFiles(
                    "test-bucket/test-dir/test-1.parquet"));
        }

        @Test
        void shouldExpandDirectoryWithMultipleFilesInside() {
            // Given
            uploadFileToS3("test-dir/test-1.parquet");
            uploadFileToS3("test-dir/test-2.parquet");
            String json = "{" +
                    "\"id\":\"id\"," +
                    "\"tableName\":\"test-table\"," +
                    "\"files\":[" +
                    "   \"test-bucket/test-dir\"" +
                    "]}";

            // When
            Optional<IngestJob> job = ingestJobMessageHandler.deserialiseAndValidate(json);

            // Then
            assertThat(job).contains(jobWithFiles(
                    "test-bucket/test-dir/test-1.parquet",
                    "test-bucket/test-dir/test-2.parquet"));
        }

        @Test
        void shouldExpandDirectoryWithFileInsideNestedDirectory() {
            // Given
            uploadFileToS3("test-dir/nested-dir/test-1.parquet");
            String json = "{" +
                    "\"id\":\"id\"," +
                    "\"tableName\":\"test-table\"," +
                    "\"files\":[" +
                    "   \"test-bucket/test-dir\"" +
                    "]}";

            // When
            Optional<IngestJob> job = ingestJobMessageHandler.deserialiseAndValidate(json);

            // Then
            assertThat(job).contains(jobWithFiles(
                    "test-bucket/test-dir/nested-dir/test-1.parquet"));
        }

        @Test
        void shouldExpandMultipleDirectories() {
            // Given
            uploadFileToS3("test-dir-1/test-1.parquet");
            uploadFileToS3("test-dir-2/test-2.parquet");
            String json = "{" +
                    "\"id\":\"id\"," +
                    "\"tableName\":\"test-table\"," +
                    "\"files\":[" +
                    "   \"test-bucket/test-dir-1\"," +
                    "   \"test-bucket/test-dir-2\"" +
                    "]}";

            // When
            Optional<IngestJob> job = ingestJobMessageHandler.deserialiseAndValidate(json);

            // Then
            assertThat(job).contains(jobWithFiles(
                    "test-bucket/test-dir-1/test-1.parquet",
                    "test-bucket/test-dir-2/test-2.parquet"));
        }
    }

    @Nested
    @DisplayName("Report validation failures")
    class ReportValidationFailures {
        @Test
        void shouldReportValidationFailureIfFileDoesNotExist() {
            // Given
            String json = "{" +
                    "\"id\": \"id\"," +
                    "\"tableName\": \"test-table\"," +
                    "\"files\": [" +
                    "    \"test-bucket/not-a-file\"" +
                    "]}";

            // When
            Optional<IngestJob> job = ingestJobMessageHandler.deserialiseAndValidate(json);

            // Then
            assertThat(job).isNotPresent();
            assertThat(ingestJobStatusStore.getInvalidJobs())
                    .containsExactly(jobStatus("id",
                            rejectedRun("id", json, validationTime, "Could not find one or more files")));
        }

        @Test
        void shouldReportValidationFailureWhenOneFileExistsAndOneDoesNotExist() {
            // Given
            uploadFileToS3("test-file.parquet");
            String json = "{" +
                    "\"id\": \"id\"," +
                    "\"tableName\": \"test-table\"," +
                    "\"files\": [" +
                    "    \"test-bucket/test-file.parquet\"," +
                    "    \"test-bucket/not-a-file\"" +
                    "]}";

            // When
            Optional<IngestJob> job = ingestJobMessageHandler.deserialiseAndValidate(json);

            // Then
            assertThat(job).isNotPresent();
            assertThat(ingestJobStatusStore.getInvalidJobs())
                    .containsExactly(jobStatus("id",
                            rejectedRun("id", json, validationTime, "Could not find one or more files")));
        }
    }

    private void uploadFileToS3(String filePath) {
        s3Client.putObject(TEST_BUCKET, filePath, "test");
    }

    private static IngestJob jobWithFiles(String... files) {
        return IngestJob.builder()
                .id("id")
                .tableName(TEST_TABLE).tableId(TEST_TABLE_ID)
                .files(List.of(files)).build();
    }

    private static Configuration createHadoopConfiguration() {
        Configuration conf = new Configuration();
        conf.set("fs.s3a.bucket.test-bucket.endpoint", localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        conf.set("fs.s3a.access.key", localStackContainer.getAccessKey());
        conf.set("fs.s3a.secret.key", localStackContainer.getSecretKey());
        return conf;
    }
}
