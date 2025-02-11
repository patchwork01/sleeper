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
package sleeper.clients.status.report.partitions;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import sleeper.clients.testutil.ToStringPrintStream;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.configuration.properties.table.S3TableProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.configuration.properties.table.TablePropertiesStore;
import sleeper.configuration.table.index.DynamoDBTableIndexCreator;
import sleeper.core.CommonTestConstants;
import sleeper.core.schema.Field;
import sleeper.core.schema.Schema;
import sleeper.core.schema.type.StringType;
import sleeper.core.statestore.StateStore;
import sleeper.statestore.StateStoreFactory;
import sleeper.statestore.s3.S3StateStoreCreator;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static sleeper.clients.status.report.partitions.PartitionStatusReportTestHelper.createRootPartitionWithTwoChildren;
import static sleeper.clients.testutil.ClientTestUtils.example;
import static sleeper.configuration.properties.InstancePropertiesTestHelper.createTestInstanceProperties;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.DATA_BUCKET;
import static sleeper.configuration.properties.instance.CommonProperty.ID;
import static sleeper.configuration.properties.table.TablePropertiesTestHelper.createTestTableProperties;
import static sleeper.configuration.properties.table.TableProperty.PARTITION_SPLIT_THRESHOLD;
import static sleeper.configuration.properties.table.TableProperty.TABLE_NAME;
import static sleeper.configuration.testutils.LocalStackAwsV1ClientHelper.buildAwsV1Client;
import static sleeper.utils.HadoopConfigurationLocalStackUtils.getHadoopConfiguration;

@Testcontainers
public class PartitionsStatusReportIT {

    @Container
    public static LocalStackContainer localStackContainer = new LocalStackContainer(
            DockerImageName.parse(CommonTestConstants.LOCALSTACK_DOCKER_IMAGE))
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.DYNAMODB);

    private final AmazonS3 s3 = buildAwsV1Client(localStackContainer, LocalStackContainer.Service.S3, AmazonS3ClientBuilder.standard());
    private final AmazonDynamoDB dynamoDB = buildAwsV1Client(localStackContainer, LocalStackContainer.Service.DYNAMODB, AmazonDynamoDBClientBuilder.standard());
    private final InstanceProperties instanceProperties = createTestInstance();
    private final TablePropertiesStore tablePropertiesStore = S3TableProperties.getStore(instanceProperties, s3, dynamoDB);
    private final Schema schema = Schema.builder().rowKeyFields(new Field("key", new StringType())).build();
    private final TableProperties tableProperties = createTestTable(
            tableProperties -> tableProperties.setNumber(PARTITION_SPLIT_THRESHOLD, 10));

    @Test
    void shouldGetReportWhenTwoLeafPartitionsBothNeedSplitting() throws Exception {
        // Given
        createRootPartitionWithTwoChildren()
                .singleFileInEachLeafPartitionWithRecords(100)
                .setupStateStore(stateStore());

        // When / Then
        assertThat(runReport()).isEqualTo(
                example("reports/partitions/rootWithTwoChildrenBothNeedSplitting.txt"));
    }

    private String runReport() throws Exception {
        ToStringPrintStream out = new ToStringPrintStream();
        PartitionsStatusReportArguments.fromArgs(instanceProperties.get(ID), tableProperties.get(TABLE_NAME))
                .runReport(s3, dynamoDB, out.getPrintStream());
        return out.toString();
    }

    private StateStore stateStore() {
        return new StateStoreFactory(dynamoDB, instanceProperties, getHadoopConfiguration(localStackContainer))
                .getStateStore(tableProperties);
    }

    private InstanceProperties createTestInstance() {
        InstanceProperties properties = createTestInstanceProperties(s3);
        s3.createBucket(properties.get(DATA_BUCKET));
        DynamoDBTableIndexCreator.create(dynamoDB, properties);
        new S3StateStoreCreator(properties, dynamoDB).create();
        return properties;
    }

    private TableProperties createTestTable(Consumer<TableProperties> tableConfig) {
        TableProperties tableProperties = createTestTableProperties(instanceProperties, schema);
        tableConfig.accept(tableProperties);
        tablePropertiesStore.save(tableProperties);
        return tableProperties;
    }
}
