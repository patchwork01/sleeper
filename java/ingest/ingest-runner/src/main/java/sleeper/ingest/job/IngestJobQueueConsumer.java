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

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.core.iterator.IteratorException;
import sleeper.core.statestore.StateStoreException;
import sleeper.core.table.TableIndex;
import sleeper.ingest.IngestResult;
import sleeper.ingest.job.status.IngestJobStatusStore;
import sleeper.job.common.action.ActionException;
import sleeper.job.common.action.DeleteMessageAction;
import sleeper.job.common.action.MessageReference;
import sleeper.job.common.action.thread.PeriodicActionRunnable;
import sleeper.utils.HadoopPathUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.INGEST_JOB_QUEUE_URL;
import static sleeper.configuration.properties.instance.CommonProperty.ID;
import static sleeper.configuration.properties.instance.CommonProperty.METRICS_NAMESPACE;
import static sleeper.configuration.properties.instance.CommonProperty.QUEUE_VISIBILITY_TIMEOUT_IN_SECONDS;
import static sleeper.configuration.properties.instance.IngestProperty.INGEST_JOB_QUEUE_WAIT_TIME;
import static sleeper.configuration.properties.instance.IngestProperty.INGEST_KEEP_ALIVE_PERIOD_IN_SECONDS;

/**
 * An IngestJobQueueConsumer pulls ingest jobs off an SQS queue and runs them.
 */
public class IngestJobQueueConsumer implements IngestJobSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(IngestJobQueueConsumer.class);

    private final AmazonSQS sqsClient;
    private final AmazonCloudWatch cloudWatchClient;
    private final InstanceProperties instanceProperties;
    private final String sqsJobQueueUrl;
    private final int keepAlivePeriod;
    private final int visibilityTimeoutInSeconds;
    private final IngestJobMessageHandler<IngestJob> ingestJobMessageHandler;

    public IngestJobQueueConsumer(AmazonSQS sqsClient,
                                  AmazonCloudWatch cloudWatchClient,
                                  InstanceProperties instanceProperties,
                                  Configuration configuration,
                                  TableIndex tableIndex,
                                  IngestJobStatusStore ingestJobStatusStore) {
        this.sqsClient = sqsClient;
        this.cloudWatchClient = cloudWatchClient;
        this.instanceProperties = instanceProperties;
        this.sqsJobQueueUrl = instanceProperties.get(INGEST_JOB_QUEUE_URL);
        this.keepAlivePeriod = instanceProperties.getInt(INGEST_KEEP_ALIVE_PERIOD_IN_SECONDS);
        this.visibilityTimeoutInSeconds = instanceProperties.getInt(QUEUE_VISIBILITY_TIMEOUT_IN_SECONDS);
        this.ingestJobMessageHandler = messageHandler(instanceProperties, configuration, tableIndex, ingestJobStatusStore).build();
    }

    public static IngestJobMessageHandler.Builder<IngestJob> messageHandler(
            InstanceProperties instanceProperties,
            Configuration configuration,
            TableIndex tableIndex,
            IngestJobStatusStore ingestJobStatusStore) {
        return IngestJobMessageHandler.forIngestJob()
                .tableIndex(tableIndex)
                .ingestJobStatusStore(ingestJobStatusStore)
                .expandDirectories(files -> HadoopPathUtils.expandDirectories(files, configuration, instanceProperties));
    }

    @Override
    public void consumeJobs(IngestJobHandler runJob) throws IteratorException, StateStoreException, IOException {
        while (true) {
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(sqsJobQueueUrl)
                    .withMaxNumberOfMessages(1)
                    .withWaitTimeSeconds(instanceProperties.getInt(INGEST_JOB_QUEUE_WAIT_TIME));
            ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
            List<Message> messages = receiveMessageResult.getMessages();
            if (messages.isEmpty()) {
                LOGGER.info("Finishing as no jobs have been received");
                break;
            }
            LOGGER.info("Received message {}", messages.get(0).getBody());
            Optional<IngestJob> ingestJob = ingestJobMessageHandler.deserialiseAndValidate(messages.get(0).getBody());
            if (ingestJob.isPresent()) {
                long recordsWritten = ingest(ingestJob.get(), messages.get(0).getReceiptHandle(), runJob);
                LOGGER.info("{} records were written", recordsWritten);
            } else {
                LOGGER.info("Could not deserialise ingest job {}, skipping", ingestJob);
            }
        }
    }

    private long ingest(IngestJob job, String receiptHandle, IngestJobHandler runJob) throws IteratorException, StateStoreException, IOException {
        // Create background thread to keep messages alive
        MessageReference messageReference = new MessageReference(sqsClient, sqsJobQueueUrl, "Ingest job " + job.getId(), receiptHandle);
        PeriodicActionRunnable changeTimeoutRunnable = new PeriodicActionRunnable(
                messageReference.changeVisibilityTimeoutAction(visibilityTimeoutInSeconds), keepAlivePeriod);
        changeTimeoutRunnable.start();
        LOGGER.info("Ingest job {}: Created background thread to keep SQS messages alive (period is {} seconds)",
                job.getId(), keepAlivePeriod);

        IngestResult result;
        try {
            // Run the ingest
            result = runJob.ingest(job);
        } finally {
            LOGGER.info("Ingest job {}: Stopping background thread to keep SQS messages alive",
                    job.getId());
            changeTimeoutRunnable.stop();
        }

        // Delete messages from SQS queue
        LOGGER.info("Ingest job {}: Deleting messages from queue", job.getId());
        DeleteMessageAction deleteAction = messageReference.deleteAction();
        try {
            deleteAction.call();
        } catch (ActionException e) {
            LOGGER.error("Ingest job {}: ActionException deleting message with handle {}", job.getId(), receiptHandle);
        }

        // Update metrics
        String metricsNamespace = instanceProperties.get(METRICS_NAMESPACE);
        String instanceId = instanceProperties.get(ID);
        cloudWatchClient.putMetricData(new PutMetricDataRequest()
                .withNamespace(metricsNamespace)
                .withMetricData(new MetricDatum()
                        .withMetricName("StandardIngestRecordsWritten")
                        .withValue((double) result.getRecordsWritten())
                        .withUnit(StandardUnit.Count)
                        .withDimensions(
                                new Dimension().withName("instanceId").withValue(instanceId),
                                new Dimension().withName("tableName").withValue(job.getTableName())
                        )));

        return result.getRecordsWritten();
    }
}
