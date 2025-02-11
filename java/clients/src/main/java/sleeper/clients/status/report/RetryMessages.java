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
package sleeper.clients.status.report;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import sleeper.clients.util.ClientUtils;
import sleeper.configuration.properties.instance.InstanceProperties;

import java.util.HashSet;
import java.util.Set;

import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.COMPACTION_JOB_DLQ_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.COMPACTION_JOB_QUEUE_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.INGEST_JOB_DLQ_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.INGEST_JOB_QUEUE_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.QUERY_DLQ_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.QUERY_QUEUE_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.SPLITTING_COMPACTION_JOB_DLQ_URL;
import static sleeper.configuration.properties.instance.CdkDefinedInstanceProperty.SPLITTING_COMPACTION_JOB_QUEUE_URL;

/**
 * A utility class to take messages off a dead-letter queue and send them back
 * to the original queue.
 */
public class RetryMessages {
    private final InstanceProperties instanceProperties;
    private final AmazonSQS sqsClient;
    private final String stack;
    private final int maxMessages;

    public RetryMessages(InstanceProperties instanceProperties,
                         AmazonSQS sqsClient,
                         String stack,
                         int maxMessages) {
        this.instanceProperties = instanceProperties;
        this.sqsClient = sqsClient;
        this.stack = stack;
        this.maxMessages = maxMessages;
    }

    public void run() {
        Pair<String, String> queueAndDLQueueUrls = getQueueAndDLQueueUrls(stack);
        String originalQueueUrl = queueAndDLQueueUrls.getLeft();
        String deadLetterUrl = queueAndDLQueueUrls.getRight();

        int count = 0;
        while (count < maxMessages) {
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(deadLetterUrl)
                    .withMaxNumberOfMessages(Math.min(maxMessages, 10))
                    .withWaitTimeSeconds(1); // Must be >= 0 and <= 20
            ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
            if (receiveMessageResult.getMessages().isEmpty()) {
                System.out.println("Received no messages, terminating");
                break;
            }
            System.out.println("Received " + receiveMessageResult.getMessages().size() + " messages");
            for (Message message : receiveMessageResult.getMessages()) {
                System.out.println("Received message with id " + message.getMessageId());
                SendMessageRequest sendMessageRequest = new SendMessageRequest()
                        .withQueueUrl(originalQueueUrl)
                        .withMessageBody(message.getBody());
                sqsClient.sendMessage(sendMessageRequest);
                System.out.println("Sent message back to original queue");
                count++;
            }
        }
    }

    private Pair<String, String> getQueueAndDLQueueUrls(String stack) {
        switch (stack) {
            case "compaction":
                return new ImmutablePair<>(instanceProperties.get(COMPACTION_JOB_QUEUE_URL), instanceProperties.get(COMPACTION_JOB_DLQ_URL));
            case "splittingcompaction":
                return new ImmutablePair<>(instanceProperties.get(SPLITTING_COMPACTION_JOB_QUEUE_URL), instanceProperties.get(SPLITTING_COMPACTION_JOB_DLQ_URL));
            case "ingest":
                return new ImmutablePair<>(instanceProperties.get(INGEST_JOB_QUEUE_URL), instanceProperties.get(INGEST_JOB_DLQ_URL));
            case "query":
                return new ImmutablePair<>(instanceProperties.get(QUERY_QUEUE_URL), instanceProperties.get(QUERY_DLQ_URL));
            default:
                throw new IllegalArgumentException("Unknown stack");
        }
    }

    public static void main(String[] args) {
        if (3 != args.length) {
            throw new IllegalArgumentException("Usage: <instance-id> [compaction|splittingcompaction|ingest|query] <max-messages>");
        }
        Set<String> validStacks = new HashSet<>();
        validStacks.add("compaction");
        validStacks.add("splittingcompaction");
        validStacks.add("ingest");
        validStacks.add("query");
        String stack = args[1];
        if (!validStacks.contains(stack)) {
            System.out.println("Invalid stack: must be one of compaction, splittingcompaction, ingest, query.");
            return;
        }
        int maxMessages = Integer.parseInt(args[2]);

        AmazonS3 amazonS3 = AmazonS3ClientBuilder.defaultClient();
        InstanceProperties instanceProperties = ClientUtils.getInstanceProperties(amazonS3, args[0]);
        amazonS3.shutdown();

        AmazonSQS sqsClient = AmazonSQSClientBuilder.defaultClient();

        RetryMessages retryMessages = new RetryMessages(instanceProperties, sqsClient, stack, maxMessages);
        retryMessages.run();

        sqsClient.shutdown();
    }
}
