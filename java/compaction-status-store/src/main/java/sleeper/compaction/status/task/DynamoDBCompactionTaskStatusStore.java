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
package sleeper.compaction.status.task;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sleeper.compaction.status.CompactionStatusStoreException;
import sleeper.compaction.task.CompactionTaskStatus;
import sleeper.compaction.task.CompactionTaskStatusStore;
import sleeper.configuration.properties.InstanceProperties;
import sleeper.configuration.properties.UserDefinedInstanceProperty;

import java.util.Map;

import static sleeper.compaction.status.DynamoDBAttributes.createStringAttribute;
import static sleeper.compaction.status.DynamoDBUtils.instanceTableName;
import static sleeper.compaction.status.task.DynamoDBCompactionTaskStatusFormat.TASK_ID;
import static sleeper.configuration.properties.UserDefinedInstanceProperty.ID;

public class DynamoDBCompactionTaskStatusStore implements CompactionTaskStatusStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBCompactionTaskStatusStore.class);
    private final AmazonDynamoDB dynamoDB;
    private final String statusTableName;
    private Long timeToLive;

    private DynamoDBCompactionTaskStatusStore(AmazonDynamoDB dynamoDB, InstanceProperties properties) {
        this.dynamoDB = dynamoDB;
        this.statusTableName = taskStatusTableName(properties.get(ID));
        this.timeToLive = properties.getLong(UserDefinedInstanceProperty.COMPACTION_JOB_STATUS_TTL_IN_SECONDS) * 1000;
    }

    @Override
    public void taskStarted(CompactionTaskStatus taskStatus) {
        try {
            PutItemResult result = putItem(DynamoDBCompactionTaskStatusFormat.createTaskStartedRecord(taskStatus, taskStatus.getStartedStatus().getStartTime(), timeToLive));
            LOGGER.debug("Put created event for job {} to table {}, capacity consumed = {}",
                    taskStatus.getTaskId(), statusTableName, result.getConsumedCapacity().getCapacityUnits());
        } catch (RuntimeException e) {
            throw new CompactionStatusStoreException("Failed putItem in taskCreated", e);
        }
    }

    @Override
    public void taskFinished(CompactionTaskStatus taskStatus) {
        try {
            PutItemResult result = putItem(DynamoDBCompactionTaskStatusFormat.createTaskFinishedRecord(taskStatus, taskStatus.getFinishedStatus().getFinishTime(), timeToLive));
            LOGGER.debug("Put created event for job {} to table {}, capacity consumed = {}",
                    taskStatus.getTaskId(), statusTableName, result.getConsumedCapacity().getCapacityUnits());
        } catch (RuntimeException e) {
            throw new CompactionStatusStoreException("Failed putItem in taskCreated", e);
        }
    }

    @Override
    public CompactionTaskStatus getTask(String taskId) {
        QueryResult result = dynamoDB.query(new QueryRequest()
                .withTableName(statusTableName)
                .addKeyConditionsEntry(TASK_ID, new Condition()
                        .withAttributeValueList(createStringAttribute(taskId))
                        .withComparisonOperator(ComparisonOperator.EQ)));
        return DynamoDBCompactionTaskStatusFormat.streamJobStatuses(result.getItems())
                .findFirst().orElse(null);
    }

    private PutItemResult putItem(Map<String, AttributeValue> item) {
        PutItemRequest putItemRequest = new PutItemRequest()
                .withItem(item)
                .withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .withTableName(statusTableName);
        return dynamoDB.putItem(putItemRequest);
    }

    public static DynamoDBCompactionTaskStatusStore from(AmazonDynamoDB dynamoDB, InstanceProperties properties) {
        return new DynamoDBCompactionTaskStatusStore(dynamoDB, properties);
    }

    public static String taskStatusTableName(String instanceId) {
        return instanceTableName(instanceId, "compaction-task-status");
    }
}
