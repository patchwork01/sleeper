/*
 * Copyright 2022 Crown Copyright
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

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sleeper.configuration.jars.ObjectFactory;
import sleeper.configuration.jars.ObjectFactoryException;
import sleeper.configuration.properties.InstanceProperties;
import sleeper.configuration.properties.table.TablePropertiesProvider;
import sleeper.core.iterator.IteratorException;
import sleeper.ingest.status.store.task.DynamoDBIngestTaskStatusStore;
import sleeper.ingest.task.IngestTaskRunner;
import sleeper.ingest.task.IngestTaskStatusStore;
import sleeper.statestore.StateStoreException;
import sleeper.statestore.StateStoreProvider;
import sleeper.utils.HadoopConfigurationProvider;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.io.IOException;
import java.util.UUID;

public class IngestJobQueueConsumerRunner {
    private IngestJobQueueConsumerRunner() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestJobQueueConsumerRunner.class);

    public static void main(String[] args) throws IOException, StateStoreException, IteratorException, ObjectFactoryException {
        if (1 != args.length) {
            System.err.println("Error: must have 1 argument (s3Bucket)");
            System.exit(1);
        }

        long startTime = System.currentTimeMillis();
        AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
        AmazonSQS sqsClient = AmazonSQSClientBuilder.defaultClient();
        AmazonCloudWatch cloudWatchClient = AmazonCloudWatchClientBuilder.defaultClient();
        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();

        String s3Bucket = args[0];
        InstanceProperties instanceProperties = new InstanceProperties();
        instanceProperties.loadFromS3(s3Client, s3Bucket);

        ObjectFactory objectFactory = new ObjectFactory(instanceProperties, s3Client, "/tmp");

        String localDir = "/mnt/scratch";
        TablePropertiesProvider tablePropertiesProvider = new TablePropertiesProvider(s3Client, instanceProperties);
        StateStoreProvider stateStoreProvider = new StateStoreProvider(dynamoDBClient, instanceProperties, HadoopConfigurationProvider.getConfigurationForECS(instanceProperties));
        IngestTaskStatusStore taskStore = DynamoDBIngestTaskStatusStore.from(dynamoDBClient, instanceProperties);
        String taskId = UUID.randomUUID().toString();
        IngestJobRunner ingestJobRunner = new IngestJobRunner(
                objectFactory,
                instanceProperties,
                tablePropertiesProvider,
                stateStoreProvider,
                localDir,
                S3AsyncClient.create(),
                HadoopConfigurationProvider.getConfigurationForECS(instanceProperties));
        IngestJobQueueConsumer queueConsumer = new IngestJobQueueConsumer(sqsClient, cloudWatchClient, instanceProperties);
        IngestTaskRunner ingestTaskRunner = new IngestTaskRunner(
                queueConsumer, taskId, taskStore, ingestJobRunner::ingest);
        ingestTaskRunner.run();

        s3Client.shutdown();
        LOGGER.info("Shut down s3Client");
        sqsClient.shutdown();
        LOGGER.info("Shut down sqsClient");
        dynamoDBClient.shutdown();
        LOGGER.info("Shut down dynamoDBClient");
        long finishTime = System.currentTimeMillis();
        double runTimeInSeconds = (finishTime - startTime) / 1000.0;
        LOGGER.info("IngestFromIngestJobsQueueRunner total run time = {}", runTimeInSeconds);
    }
}
