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
package sleeper.clients.status.update;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.S3TableProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TablePropertiesStore;
import sleeper.configuration.table.index.DynamoDBTableIndexCreator;
import sleeper.core.CommonTestConstants;
import sleeper.core.partition.Partition;
import sleeper.core.partition.PartitionTree;
import sleeper.core.partition.PartitionsBuilder;
import sleeper.core.schema.Field;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.StringType;
import sleeper.core.statestore.FileInfo;
import sleeper.core.statestore.StateStore;
import sleeper.core.statestore.StateStoreException;
import sleeper.statestore.dynamodb.DynamoDBStateStore;
import sleeper.statestore.dynamodb.DynamoDBStateStoreCreator;
import sleeper.statestore.s3.S3StateStore;
import sleeper.statestore.s3.S3StateStoreCreator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sleeper.configuration.properties.InstancePropertiesTestHelper.createTestInstanceProperties;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.CONFIG_BUCKET;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.DATA_BUCKET;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.REVISION_TABLENAME;
import static sleeper.configuration.properties.instance.CommonProperty.ID;
import static sleeper.configuration.properties.table.TablePropertiesTestHelper.createTestTableProperties;
import static sleeper.configuration.properties.table.TableProperty.GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION;
import static sleeper.configuration.properties.table.TableProperty.STATESTORE_CLASSNAME;
import static sleeper.configuration.properties.table.TableProperty.TABLE_ID;
import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;
import static sleeper.configuration.testutils.LocalStackAwsV1ClientHelper.buildAwsV1Client;
import static sleeper.statestore.s3.S3StateStore.CURRENT_FILES_REVISION_ID_KEY;
import static sleeper.statestore.s3.S3StateStore.CURRENT_PARTITIONS_REVISION_ID_KEY;
import static sleeper.statestore.s3.S3StateStore.CURRENT_REVISION;
import static sleeper.statestore.s3.S3StateStore.REVISION_ID_KEY;

@Testcontainers
public class ReinitialiseTableIT {
    private static final String FILE_SHOULD_NOT_BE_DELETED_1 = "file0.parquet";
    private static final String FILE_SHOULD_NOT_BE_DELETED_2 = "for_ingest/file0.parquet";
    private static final String FILE_SHOULD_NOT_BE_DELETED_3 = "partition.parquet";
    private static final String SPLIT_PARTITION_STRING_1 = "alpha";
    private static final String SPLIT_PARTITION_STRING_2 = "beta";
    private static final String S3_STATE_STORE_PARTITIONS_FILENAME = "partitions/file4.parquet";
    private static final String S3_STATE_STORE_FILES_FILENAME = "files/file5.parquet";
    private static final Schema KEY_VALUE_SCHEMA = Schema.builder()
            .rowKeyFields(new Field("key", new StringType()))
            .valueFields(new Field("value1", new StringType()), new Field("value2", new StringType()))
            .build();

    @Container
    public static LocalStackContainer localStackContainer = new LocalStackContainer(DockerImageName.parse(CommonTestConstants.LOCALSTACK_DOCKER_IMAGE))
            .withServices(LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.S3);

    private final AmazonDynamoDB dynamoDBClient = buildAwsV1Client(localStackContainer, LocalStackContainer.Service.DYNAMODB, AmazonDynamoDBClientBuilder.standard());
    private final AmazonS3 s3Client = buildAwsV1Client(localStackContainer, LocalStackContainer.Service.S3, AmazonS3ClientBuilder.standard());

    private final InstanceProperties instanceProperties = createTestInstanceProperties();
    private final TableProperties tableProperties = createTestTableProperties(instanceProperties, KEY_VALUE_SCHEMA);
    private final TablePropertiesStore tablePropertiesStore = S3TableProperties.getStore(instanceProperties, s3Client, dynamoDBClient);
    private final String s3StateStorePath = tableProperties.get(TABLE_ID) + "/statestore";

    @TempDir
    public Path tempDir;

    @BeforeEach
    public void beforeEach() {
        s3Client.createBucket(instanceProperties.get(CONFIG_BUCKET));
        s3Client.createBucket(instanceProperties.get(DATA_BUCKET));
        DynamoDBTableIndexCreator.create(dynamoDBClient, instanceProperties);
    }

    @AfterEach
    public void afterEach() {
        s3Client.shutdown();
        dynamoDBClient.shutdown();
    }

    @Test
    public void shouldThrowExceptionIfInstanceIdIsEmpty() {
        // Given
        String tableName = UUID.randomUUID().toString();

        // When
        assertThatThrownBy(() -> new ReinitialiseTable(s3Client, dynamoDBClient, "", tableName, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldThrowExceptionIfTableNameIsEmpty() {
        assertThatThrownBy(() -> new ReinitialiseTable(s3Client, dynamoDBClient, instanceProperties.get(ID), "", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("Using DynamoDB state store")
    class UsingDynamoDBStateStore {

        @BeforeEach
        public void setup() {
            tableProperties.set(STATESTORE_CLASSNAME, DynamoDBStateStore.class.getName());
            new DynamoDBStateStoreCreator(instanceProperties, dynamoDBClient).create();
        }

        @Test
        public void shouldDeleteActiveAndGCFilesByDefault() throws Exception {
            // Given
            saveProperties();
            saveTableDataFiles();
            DynamoDBStateStore dynamoStateStore = setupDynamoStateStore(tableProperties);

            // When
            reinitialiseTable(tableProperties);

            // Then
            assertThat(dynamoStateStore.getActiveFiles()).isEmpty();
            assertThat(dynamoStateStore.getReadyForGCFiles()).isExhausted();
            assertThat(dynamoStateStore.getAllPartitions()).hasSize(3);
            assertThat(dynamoStateStore.getLeafPartitions()).hasSize(2);
            assertOnlyObjectsWithinPartitionsAndStateStoreFilesAreasInTheTableBucketHaveBeenDeleted();
        }

        @Test
        public void shouldDeletePartitionsWhenOptionSelected() throws Exception {
            // Given
            saveProperties();
            saveTableDataFiles();
            DynamoDBStateStore dynamoStateStore = setupDynamoStateStore(tableProperties);

            // When
            reinitialiseTableAndDeletePartitions(tableProperties);

            // Then
            assertThat(dynamoStateStore.getActiveFiles()).isEmpty();
            assertThat(dynamoStateStore.getReadyForGCFiles()).isExhausted();
            assertThat(dynamoStateStore.getAllPartitions()).hasSize(1);
            assertThat(dynamoStateStore.getLeafPartitions()).hasSize(1);
            assertObjectsWithinPartitionsAndStateStoreAreaInTheTableBucketHaveBeenDeleted();
        }

        @Test
        public void shouldSetUpSplitPointsFromFileWhenOptionSelected() throws Exception {
            // Given
            saveProperties();
            saveTableDataFiles();
            DynamoDBStateStore dynamoStateStore = setupDynamoStateStore(tableProperties);
            String splitPointsFileName = createSplitPointsFile(false);

            // When
            reinitialiseTableFromSplitPoints(tableProperties, splitPointsFileName);

            // Then
            assertThat(dynamoStateStore.getActiveFiles()).isEmpty();
            assertThat(dynamoStateStore.getReadyForGCFiles()).isExhausted();
            List<Partition> partitionsList = dynamoStateStore.getAllPartitions();
            assertThat(partitionsList).hasSize(5);
            assertThat(dynamoStateStore.getLeafPartitions()).hasSize(3);
            assertThat(partitionsList)
                    .extracting(partition -> partition.getRegion().getRange("key").getMin().toString())
                    .contains(SPLIT_PARTITION_STRING_1, SPLIT_PARTITION_STRING_2);

            assertObjectsWithinPartitionsAndStateStoreAreaInTheTableBucketHaveBeenDeleted();
        }

        @Test
        public void shouldHandleEncodedSplitPointsFileWhenOptionSelected() throws Exception {
            // Given
            saveProperties();
            saveTableDataFiles();
            DynamoDBStateStore dynamoStateStore = setupDynamoStateStore(tableProperties);
            String splitPointsFileName = createSplitPointsFile(true);

            // When
            reinitialiseTableFromSplitPointsEncoded(tableProperties, splitPointsFileName);

            // Then
            assertThat(dynamoStateStore.getActiveFiles()).isEmpty();
            assertThat(dynamoStateStore.getReadyForGCFiles()).isExhausted();
            List<Partition> partitionsList = dynamoStateStore.getAllPartitions();
            assertThat(partitionsList).hasSize(5);
            assertThat(dynamoStateStore.getLeafPartitions()).hasSize(3);
            assertThat(partitionsList)
                    .extracting(partition -> partition.getRegion().getRange("key").getMin().toString())
                    .contains(SPLIT_PARTITION_STRING_1, SPLIT_PARTITION_STRING_2);

            assertObjectsWithinPartitionsAndStateStoreAreaInTheTableBucketHaveBeenDeleted();
        }
    }

    @Nested
    @DisplayName("Using S3 state store")
    class UsingS3StateStore {
        @BeforeEach
        public void setup() {
            tableProperties.set(STATESTORE_CLASSNAME, S3StateStore.class.getName());
        }

        @Test
        public void shouldDeleteFilesInfoAndObjectsInPartitionsByDefault() throws Exception {
            // Given
            saveProperties();
            saveTableDataFiles();
            S3StateStore s3StateStore = setupS3StateStore(tableProperties);

            // When
            reinitialiseTable(tableProperties);

            // Then
            assertS3StateStoreRevisionsDynamoTableNowHasCorrectVersions("1", "2");
            assertThat(s3StateStore.getActiveFiles()).isEmpty();
            assertThat(s3StateStore.getAllPartitions()).hasSize(3);
            assertThat(s3StateStore.getLeafPartitions()).hasSize(2);
            assertOnlyObjectsWithinPartitionsAndStateStoreFilesAreasInTheTableBucketHaveBeenDeleted();
        }

        @Test
        public void shouldDeletePartitionsWhenOptionSelected() throws Exception {
            // Given
            saveProperties();
            saveTableDataFiles();
            S3StateStore s3StateStore = setupS3StateStore(tableProperties);

            // When
            reinitialiseTableAndDeletePartitions(tableProperties);

            // Then
            assertS3StateStoreRevisionsDynamoTableNowHasCorrectVersions("1", "1");

            assertThat(s3StateStore.getActiveFiles()).isEmpty();
            assertThat(s3StateStore.getAllPartitions()).hasSize(1);
            assertThat(s3StateStore.getLeafPartitions()).hasSize(1);

            assertObjectsWithinPartitionsAndStateStoreAreaInTheTableBucketHaveBeenDeletedWithS3StateStore();
        }


        @Test
        public void shouldSetUpSplitPointsFromFileWhenOptionSelected() throws Exception {
            // Given
            saveProperties();
            saveTableDataFiles();
            S3StateStore s3StateStore = setupS3StateStore(tableProperties);
            String splitPointsFileName = createSplitPointsFile(false);

            // When
            reinitialiseTableFromSplitPoints(tableProperties, splitPointsFileName);

            // Then
            assertS3StateStoreRevisionsDynamoTableNowHasCorrectVersions("1", "1");

            assertThat(s3StateStore.getActiveFiles()).isEmpty();

            List<Partition> partitionsList = s3StateStore.getAllPartitions();
            assertThat(partitionsList).hasSize(5);
            assertThat(s3StateStore.getLeafPartitions()).hasSize(3);
            assertThat(partitionsList)
                    .extracting(partition -> partition.getRegion().getRange("key").getMin().toString())
                    .contains(SPLIT_PARTITION_STRING_1, SPLIT_PARTITION_STRING_2);

            assertObjectsWithinPartitionsAndStateStoreAreaInTheTableBucketHaveBeenDeletedWithS3StateStore();
        }


        @Test
        public void shouldHandleEncodedSplitPointsFileWhenOptionSelected() throws Exception {
            // Given
            saveProperties();
            saveTableDataFiles();
            S3StateStore s3StateStore = setupS3StateStore(tableProperties);
            String splitPointsFileName = createSplitPointsFile(true);

            // When
            reinitialiseTableFromSplitPointsEncoded(tableProperties, splitPointsFileName);

            // Then
            assertS3StateStoreRevisionsDynamoTableNowHasCorrectVersions("1", "1");

            assertThat(s3StateStore.getActiveFiles()).isEmpty();

            List<Partition> partitionsList = s3StateStore.getAllPartitions();
            assertThat(partitionsList).hasSize(5);
            assertThat(s3StateStore.getLeafPartitions()).hasSize(3);
            assertThat(partitionsList)
                    .extracting(partition -> partition.getRegion().getRange("key").getMin().toString())
                    .contains(SPLIT_PARTITION_STRING_1, SPLIT_PARTITION_STRING_2);

            assertObjectsWithinPartitionsAndStateStoreAreaInTheTableBucketHaveBeenDeletedWithS3StateStore();
        }
    }

    private void assertS3StateStoreRevisionsDynamoTableNowHasCorrectVersions(String expectedFilesVersion,
                                                                             String expectedPartitionsVersion) {
        // - The revisions file should have two entries one for partitions and one for files and both should now be
        //   set to version 00000000000001
        ScanRequest scanRequest = new ScanRequest()
                .withTableName(instanceProperties.get(REVISION_TABLENAME))
                .withConsistentRead(true);
        ScanResult scanResult = dynamoDBClient.scan(scanRequest);
        assertThat(scanResult.getItems()).hasSize(2);
        String filesVersion = "";
        String partitionsVersion = "";
        String versionPrefix = "00000000000";
        for (Map<String, AttributeValue> item : scanResult.getItems()) {
            if (item.get(REVISION_ID_KEY).toString().contains(CURRENT_FILES_REVISION_ID_KEY)) {
                filesVersion = item.get(CURRENT_REVISION).toString();
            }
            if (item.get(REVISION_ID_KEY).toString().contains(CURRENT_PARTITIONS_REVISION_ID_KEY)) {
                partitionsVersion = item.get(CURRENT_REVISION).toString();
            }
        }

        assertThat(filesVersion).isNotEmpty().contains(versionPrefix + expectedFilesVersion);
        assertThat(partitionsVersion).isNotEmpty().contains(versionPrefix + expectedPartitionsVersion);
    }

    private void assertObjectsWithinPartitionsAndStateStoreAreaInTheTableBucketHaveBeenDeleted() {
        String tableName = tableProperties.get(TABLE_NAME);
        assertThat(s3Client.listObjectsV2(instanceProperties.get(DATA_BUCKET))
                .getObjectSummaries())
                .extracting(S3ObjectSummary::getKey)
                .containsExactlyInAnyOrder(
                        tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_1,
                        tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_2,
                        tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_3);
    }

    private void assertObjectsWithinPartitionsAndStateStoreAreaInTheTableBucketHaveBeenDeletedWithS3StateStore() {
        String tableName = tableProperties.get(TABLE_NAME);
        assertThat(s3Client.listObjectsV2(instanceProperties.get(DATA_BUCKET))
                .getObjectSummaries())
                .extracting(S3ObjectSummary::getKey)
                .hasSize(5)
                .contains(
                        tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_1,
                        tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_2,
                        tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_3)
                .satisfies(keys -> {
                    assertThat(keys)
                            .filteredOn(key -> key.startsWith(s3StateStorePath + "/files"))
                            .hasSize(1);
                    assertThat(keys)
                            .filteredOn(key -> key.startsWith(s3StateStorePath + "/partitions"))
                            .hasSize(1);
                });
    }

    private void assertOnlyObjectsWithinPartitionsAndStateStoreFilesAreasInTheTableBucketHaveBeenDeleted() {
        String tableName = tableProperties.get(TABLE_NAME);
        ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(instanceProperties.get(DATA_BUCKET));
        ListObjectsV2Result result = s3Client.listObjectsV2(req);
        if (tableProperties.get(STATESTORE_CLASSNAME).equals(S3StateStore.class.getName())) {
            assertThat(result.getObjectSummaries())
                    .extracting(S3ObjectSummary::getKey)
                    .contains(s3StateStorePath + "/" + S3_STATE_STORE_PARTITIONS_FILENAME)
                    .doesNotContain(s3StateStorePath + "/" + S3_STATE_STORE_FILES_FILENAME);
            assertThat(result.getObjectSummaries().stream()
                    .map(S3ObjectSummary::getKey)
                    .filter(key -> key.startsWith(s3StateStorePath)))
                    .hasSize(4);
            assertThat(result.getKeyCount()).isEqualTo(7);
        } else {
            assertThat(result.getObjectSummaries())
                    .extracting(S3ObjectSummary::getKey)
                    .contains(
                            tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_1,
                            tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_2,
                            tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_3);
            assertThat(result.getKeyCount()).isEqualTo(3);
        }
    }

    private void reinitialiseTableAndDeletePartitions(TableProperties tableProperties) throws StateStoreException, IOException {
        new ReinitialiseTable(s3Client,
                dynamoDBClient, instanceProperties.get(ID), tableProperties.get(TABLE_NAME), true)
                .run();
    }

    private void reinitialiseTable(TableProperties tableProperties) throws StateStoreException, IOException {
        new ReinitialiseTable(s3Client,
                dynamoDBClient, instanceProperties.get(ID), tableProperties.get(TABLE_NAME), false)
                .run();
    }

    private void reinitialiseTableFromSplitPoints(TableProperties tableProperties, String splitPointsFile)
            throws StateStoreException, IOException {
        new ReinitialiseTableFromSplitPoints(s3Client,
                dynamoDBClient, instanceProperties.get(ID), tableProperties.get(TABLE_NAME), splitPointsFile, false)
                .run();
    }

    private void reinitialiseTableFromSplitPointsEncoded(TableProperties tableProperties, String splitPointsFile)
            throws StateStoreException, IOException {
        new ReinitialiseTableFromSplitPoints(s3Client,
                dynamoDBClient, instanceProperties.get(ID), tableProperties.get(TABLE_NAME), splitPointsFile, true)
                .run();
    }

    private void saveProperties() {
        instanceProperties.saveToS3(s3Client);
        tablePropertiesStore.save(tableProperties);
    }

    private void saveTableDataFiles() {
        String dataBucket = instanceProperties.get(DATA_BUCKET);
        String tableName = tableProperties.get(TABLE_NAME);

        s3Client.putObject(dataBucket, tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_1, "some-content");
        s3Client.putObject(dataBucket, tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_2, "some-content");
        s3Client.putObject(dataBucket, tableName + "/" + FILE_SHOULD_NOT_BE_DELETED_3, "some-content");
        s3Client.putObject(dataBucket, tableName + "/partition-root/file1.parquet", "some-content");
        s3Client.putObject(dataBucket, tableName + "/partition-1/file2.parquet", "some-content");
        s3Client.putObject(dataBucket, tableName + "/partition-2/file3.parquet", "some-content");

        if (tableProperties.get(STATESTORE_CLASSNAME).equals(S3StateStore.class.getName())) {
            s3Client.putObject(dataBucket, s3StateStorePath + "/" + S3_STATE_STORE_FILES_FILENAME, "some-content");
            s3Client.putObject(dataBucket, s3StateStorePath + "/" + S3_STATE_STORE_PARTITIONS_FILENAME, "some-content");
        }
    }

    private DynamoDBStateStore setupDynamoStateStore(TableProperties tableProperties)
            throws IOException, StateStoreException {
        //  - Create DynamoDBStateStore
        tableProperties.set(GARBAGE_COLLECTOR_DELAY_BEFORE_DELETION, "0");
        DynamoDBStateStore dynamoDBStateStore = new DynamoDBStateStore(instanceProperties, tableProperties, dynamoDBClient);
        dynamoDBStateStore.initialise();
        setupPartitionsAndAddFileInfo(dynamoDBStateStore);

        // - Check DynamoDBStateStore is set up correctly
        // - The ready for GC table should have 1 item in, and we set the GC delay to 0 to return all items.
        assertThat(dynamoDBStateStore.getReadyForGCFiles())
                .toIterable().hasSize(1);

        // - Check DynamoDBStateStore has correct active files
        assertThat(dynamoDBStateStore.getActiveFiles()).hasSize(2);

        // - Check DynamoDBStateStore has correct partitions
        List<Partition> partitionsList = dynamoDBStateStore.getAllPartitions();
        assertThat(partitionsList).hasSize(3);

        return dynamoDBStateStore;
    }

    private S3StateStore setupS3StateStore(TableProperties tableProperties) throws IOException, StateStoreException {
        //  - CreateS3StateStore
        new S3StateStoreCreator(instanceProperties, dynamoDBClient).create();
        Configuration configuration = new Configuration();
        configuration.set("fs.s3a.endpoint", localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        configuration.set("fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider");
        configuration.set("fs.s3a.access.key", localStackContainer.getAccessKey());
        configuration.set("fs.s3a.secret.key", localStackContainer.getSecretKey());
        configuration.setBoolean("fs.s3a.connection.ssl.enabled", false);

        S3StateStore s3StateStore = new S3StateStore(instanceProperties, tableProperties, dynamoDBClient, configuration);
        s3StateStore.initialise();

        setupPartitionsAndAddFileInfo(s3StateStore);

        // - Check S3StateStore is set up correctly
        // - The revisions file should have two entries one for partitions and one for files and both should now be
        //   set to version 2
        assertS3StateStoreRevisionsDynamoTableNowHasCorrectVersions("2", "2");

        // - Check S3StateStore has correct active files
        assertThat(s3StateStore.getActiveFiles()).hasSize(2);

        // - Check S3StateStore has correct partitions
        assertThat(s3StateStore.getAllPartitions()).hasSize(3);

        return s3StateStore;
    }

    private void setupPartitionsAndAddFileInfo(StateStore stateStore) throws IOException, StateStoreException {
        //  - Get root partition
        Partition rootPartition = stateStore.getAllPartitions().get(0);
        //  - Create two files of sorted data
        String folderName = createTempDirectory(tempDir, null).toString();
        String file1 = folderName + "/file1.parquet";
        String file2 = folderName + "/file2.parquet";
        String file3 = folderName + "/file3.parquet";

        FileInfo fileInfo1 = createFileInfo(file1, FileInfo.FileStatus.ACTIVE, rootPartition.getId());
        FileInfo fileInfo2 = createFileInfo(file2, FileInfo.FileStatus.ACTIVE, rootPartition.getId());
        FileInfo fileInfo3 = createFileInfo(file3, FileInfo.FileStatus.READY_FOR_GARBAGE_COLLECTION, rootPartition.getId());

        //  - Split root partition
        PartitionTree tree = new PartitionsBuilder(KEY_VALUE_SCHEMA)
                .rootFirst("root")
                .splitToNewChildren("root", "0" + "---eee", "eee---zzz", "eee")
                .buildTree();

        stateStore.atomicallyUpdatePartitionAndCreateNewOnes(
                tree.getPartition("root"), tree.getPartition("0" + "---eee"), tree.getPartition("eee---zzz"));

        //  - Update Dynamo state store with details of files
        stateStore.addFiles(Arrays.asList(fileInfo1, fileInfo2, fileInfo3));
    }

    private FileInfo createFileInfo(String filename, FileInfo.FileStatus fileStatus, String partitionId) {
        return FileInfo.builder()
                .filename(filename)
                .fileStatus(fileStatus)
                .partitionId(partitionId)
                .numberOfRecords(100L)
                .build();
    }

    private String createSplitPointsFile(boolean encoded) throws IOException {
        String splitPointsFileName = tempDir.toString() + "/split-points.txt";
        FileWriter fstream = new FileWriter(splitPointsFileName, StandardCharsets.UTF_8);
        BufferedWriter info = new BufferedWriter(fstream);
        if (encoded) {
            info.write(Base64.encodeBase64String((SPLIT_PARTITION_STRING_1.getBytes(StandardCharsets.UTF_8))));
            info.newLine();
            info.write(Base64.encodeBase64String((SPLIT_PARTITION_STRING_2.getBytes(StandardCharsets.UTF_8))));
        } else {
            info.write(SPLIT_PARTITION_STRING_1);
            info.newLine();
            info.write(SPLIT_PARTITION_STRING_2);
        }
        info.close();
        return splitPointsFileName;
    }
}
