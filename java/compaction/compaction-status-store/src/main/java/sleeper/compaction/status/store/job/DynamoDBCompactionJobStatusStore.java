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
package sleeper.compaction.status.store.job;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.Put;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsRequest;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsResult;
import com.amazonaws.services.dynamodbv2.model.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.compaction.job.CompactionJob;
import sleeper.compaction.job.CompactionJobStatusStore;
import sleeper.compaction.job.status.CompactionJobStatus;
import sleeper.compaction.status.store.CompactionStatusStoreException;
import sleeper.configuration.properties.instance.InstanceProperties;
import sleeper.core.record.process.RecordsProcessedSummary;
import sleeper.core.table.TableIdentity;
import sleeper.dynamodb.tools.DynamoDBRecordBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static sleeper.compaction.status.store.job.DynamoDBCompactionJobStatusFormat.UPDATE_TIME;
import static sleeper.compaction.status.store.job.DynamoDBCompactionJobStatusFormat.createJobCreatedUpdate;
import static sleeper.compaction.status.store.job.DynamoDBCompactionJobStatusFormat.createJobFinishedUpdate;
import static sleeper.compaction.status.store.job.DynamoDBCompactionJobStatusFormat.createJobStartedUpdate;
import static sleeper.compaction.status.store.task.DynamoDBCompactionTaskStatusFormat.UPDATE_TYPE;
import static sleeper.configuration.properties.instance.CommonProperty.ID;
import static sleeper.configuration.properties.instance.CompactionProperty.COMPACTION_JOB_STATUS_TTL_IN_SECONDS;
import static sleeper.dynamodb.tools.DynamoDBAttributes.createStringAttribute;
import static sleeper.dynamodb.tools.DynamoDBAttributes.getStringAttribute;
import static sleeper.dynamodb.tools.DynamoDBUtils.instanceTableName;
import static sleeper.dynamodb.tools.DynamoDBUtils.streamPagedItems;

public class DynamoDBCompactionJobStatusStore implements CompactionJobStatusStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBCompactionJobStatusStore.class);
    public static final String TABLE_ID = DynamoDBCompactionJobStatusFormat.TABLE_ID;
    public static final String JOB_ID = DynamoDBCompactionJobStatusFormat.JOB_ID;
    public static final String JOB_ID_AND_UPDATE = DynamoDBCompactionJobStatusFormat.JOB_ID_AND_UPDATE;
    public static final String EXPIRY_DATE = DynamoDBCompactionJobStatusFormat.EXPIRY_DATE;
    private static final String JOB_FIRST_UPDATE_TIME = "FirstUpdateTime";
    private static final String JOB_LAST_UPDATE_TIME = "LastUpdateTime";
    private static final String JOB_LAST_UPDATE_TYPE = "LastUpdateType";

    private final AmazonDynamoDB dynamoDB;
    private final String updatesTableName;
    private final String jobsTableName;
    private final int timeToLiveInSeconds;
    private final Supplier<Instant> getTimeNow;

    public DynamoDBCompactionJobStatusStore(AmazonDynamoDB dynamoDB, InstanceProperties properties) {
        this(dynamoDB, properties, Instant::now);
    }

    public DynamoDBCompactionJobStatusStore(
            AmazonDynamoDB dynamoDB, InstanceProperties properties, Supplier<Instant> getTimeNow) {
        this.dynamoDB = dynamoDB;
        this.updatesTableName = jobUpdatesTableName(properties.get(ID));
        this.jobsTableName = jobLookupTableName(properties.get(ID));
        this.timeToLiveInSeconds = properties.getInt(COMPACTION_JOB_STATUS_TTL_IN_SECONDS);
        this.getTimeNow = getTimeNow;
    }

    public static String jobUpdatesTableName(String instanceId) {
        return instanceTableName(instanceId, "compaction-job-updates");
    }

    public static String jobLookupTableName(String instanceId) {
        return instanceTableName(instanceId, "compaction-job-lookup");
    }

    @Override
    public void jobCreated(CompactionJob job) {
        try {
            save(createJobCreatedUpdate(job, jobUpdateBuilder(job)));
        } catch (RuntimeException e) {
            throw new CompactionStatusStoreException("Failed jobCreated for job " + job.getId(), e);
        }
    }

    @Override
    public void jobStarted(CompactionJob job, Instant startTime, String taskId) {
        try {
            save(createJobStartedUpdate(startTime, taskId, jobUpdateBuilder(job)));
        } catch (RuntimeException e) {
            throw new CompactionStatusStoreException("Failed jobStarted for job " + job.getId(), e);
        }
    }

    @Override
    public void jobFinished(CompactionJob job, RecordsProcessedSummary summary, String taskId) {
        try {
            save(createJobFinishedUpdate(summary, taskId, jobUpdateBuilder(job)));
        } catch (RuntimeException e) {
            throw new CompactionStatusStoreException("Failed jobFinished for job " + job.getId(), e);
        }
    }

    private void save(Map<String, AttributeValue> update) {
        TransactWriteItemsResult result = dynamoDB.transactWriteItems(new TransactWriteItemsRequest()
                .withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .withTransactItems(
                        new TransactWriteItem().withPut(new Put()
                                .withTableName(updatesTableName)
                                .withItem(update)),
                        new TransactWriteItem().withUpdate(new Update()
                                .withTableName(jobsTableName)
                                .withKey(Map.of(JOB_ID, update.get(JOB_ID)))
                                .withUpdateExpression("SET " +
                                        "#Table = :table, " +
                                        "#FirstUpdate = if_not_exists(#FirstUpdate, :update_time), " +
                                        "#LastUpdate = :update_time, " +
                                        "#LastUpdateType = :update_type, " +
                                        "#Expiry = if_not_exists(#Expiry, :expiry)")
                                .withExpressionAttributeNames(Map.of(
                                        "#Table", TABLE_ID,
                                        "#FirstUpdate", JOB_FIRST_UPDATE_TIME,
                                        "#LastUpdate", JOB_LAST_UPDATE_TIME,
                                        "#LastUpdateType", JOB_LAST_UPDATE_TYPE,
                                        "#Expiry", EXPIRY_DATE))
                                .withExpressionAttributeValues(Map.of(
                                        ":table", update.get(TABLE_ID),
                                        ":update_time", update.get(UPDATE_TIME),
                                        ":update_type", update.get(UPDATE_TYPE),
                                        ":expiry", update.get(EXPIRY_DATE))))
                ));
        List<ConsumedCapacity> consumedCapacity = result.getConsumedCapacity();
        double totalCapacity = consumedCapacity.stream().mapToDouble(ConsumedCapacity::getCapacityUnits).sum();
        LOGGER.debug("Added {} for job {}, capacity consumed = {}",
                getStringAttribute(update, UPDATE_TYPE), getStringAttribute(update, JOB_ID), totalCapacity);
    }

    private DynamoDBRecordBuilder jobUpdateBuilder(CompactionJob job) {
        Instant timeNow = getTimeNow.get();
        Instant expiry = timeNow.plus(timeToLiveInSeconds, ChronoUnit.SECONDS);
        return DynamoDBCompactionJobStatusFormat.jobUpdateBuilder(job, timeNow, expiry);
    }

    @Override
    public Stream<CompactionJobStatus> streamAllJobs(TableIdentity tableId) {
        return DynamoDBCompactionJobStatusFormat.streamJobStatuses(streamPagedItems(dynamoDB, new QueryRequest()
                .withTableName(updatesTableName)
                .withKeyConditionExpression("#TableId = :table_id")
                .withExpressionAttributeNames(Map.of("#TableId", TABLE_ID))
                .withExpressionAttributeValues(
                        Map.of(":table_id", createStringAttribute(tableId.getTableUniqueId())))
        ));
    }

    @Override
    public Optional<CompactionJobStatus> getJob(String jobId) {
        return lookupJobTableId(jobId)
                .flatMap(tableId -> DynamoDBCompactionJobStatusFormat.streamJobStatuses(streamPagedItems(dynamoDB, new QueryRequest()
                        .withTableName(updatesTableName)
                        .withKeyConditionExpression("#TableId = :table_id AND begins_with(#JobAndUpdate, :job_id)")
                        .withExpressionAttributeNames(Map.of(
                                "#TableId", TABLE_ID,
                                "#JobAndUpdate", JOB_ID_AND_UPDATE))
                        .withExpressionAttributeValues(Map.of(
                                ":table_id", createStringAttribute(tableId),
                                ":job_id", createStringAttribute(jobId + "|")))
                )).findFirst());
    }

    private Optional<String> lookupJobTableId(String jobId) {
        QueryResult result = dynamoDB.query(new QueryRequest()
                .withTableName(jobsTableName)
                .withKeyConditionExpression("#JobId = :job_id")
                .withExpressionAttributeNames(Map.of("#JobId", JOB_ID))
                .withExpressionAttributeValues(Map.of(":job_id", createStringAttribute(jobId))));
        return result.getItems().stream()
                .map(item -> getStringAttribute(item, TABLE_ID))
                .findFirst();
    }
}
