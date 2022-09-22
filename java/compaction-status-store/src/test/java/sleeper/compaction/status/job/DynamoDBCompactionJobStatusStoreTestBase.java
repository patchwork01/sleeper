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
package sleeper.compaction.status.job;

import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.groups.Tuple;
import org.junit.After;
import org.junit.Before;
import sleeper.compaction.job.CompactionJobFactory;
import sleeper.compaction.job.CompactionJobStatusStore;
import sleeper.configuration.properties.InstanceProperties;
import sleeper.configuration.properties.table.TableProperties;
import sleeper.core.partition.Partition;
import sleeper.core.partition.PartitionsBuilder;
import sleeper.core.partition.PartitionsFromSplitPoints;
import sleeper.core.schema.Schema;
import sleeper.statestore.FileInfoFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static sleeper.compaction.status.DynamoDBAttributes.getNumberAttribute;
import static sleeper.compaction.status.DynamoDBAttributes.getStringAttribute;
import static sleeper.compaction.status.job.CompactionStatusStoreTestUtils.createInstanceProperties;
import static sleeper.compaction.status.job.CompactionStatusStoreTestUtils.createSchema;
import static sleeper.compaction.status.job.CompactionStatusStoreTestUtils.createTableProperties;
import static sleeper.compaction.status.job.DynamoDBCompactionJobStatusFormat.INPUT_FILES_COUNT;
import static sleeper.compaction.status.job.DynamoDBCompactionJobStatusFormat.JOB_ID;
import static sleeper.compaction.status.job.DynamoDBCompactionJobStatusFormat.PARTITION_ID;
import static sleeper.compaction.status.job.DynamoDBCompactionJobStatusFormat.SPLIT_TO_PARTITION_IDS;
import static sleeper.compaction.status.job.DynamoDBCompactionJobStatusFormat.UPDATE_TIME;
import static sleeper.compaction.status.job.DynamoDBCompactionJobStatusFormat.UPDATE_TYPE;
import static sleeper.compaction.status.job.DynamoDBCompactionJobStatusFormat.UPDATE_TYPE_CREATED;
import static sleeper.compaction.status.job.DynamoDBCompactionJobStatusStore.jobStatusTableName;
import static sleeper.configuration.properties.UserDefinedInstanceProperty.ID;

public class DynamoDBCompactionJobStatusStoreTestBase extends DynamoDBTestBase {

    private final InstanceProperties instanceProperties = createInstanceProperties();
    private final String tableName = jobStatusTableName(instanceProperties.get(ID));
    private final Schema schema = createSchema();
    private final TableProperties tableProperties = createTableProperties(schema, instanceProperties);

    protected final CompactionJobFactory jobFactory = new CompactionJobFactory(instanceProperties, tableProperties);
    protected final CompactionJobStatusStore store = DynamoDBCompactionJobStatusStore.from(dynamoDBClient, instanceProperties);

    @Before
    public void setUp() {
        DynamoDBCompactionJobStatusStoreCreator.create(instanceProperties, dynamoDBClient);
    }

    @After
    public void tearDown() {
        dynamoDBClient.deleteTable(tableName);
    }

    protected Partition singlePartition() {
        return new PartitionsFromSplitPoints(schema, Collections.emptyList()).construct().get(0);
    }

    protected FileInfoFactory fileFactory(Partition singlePartition) {
        return fileFactory(Collections.singletonList(singlePartition));
    }

    protected FileInfoFactory fileFactoryWithPartitions(Consumer<PartitionsBuilder> partitionConfig) {
        PartitionsBuilder builder = new PartitionsBuilder(schema);
        partitionConfig.accept(builder);
        return fileFactory(builder.buildList());
    }

    private FileInfoFactory fileFactory(List<Partition> partitions) {
        return new FileInfoFactory(schema, partitions, Instant.now());
    }

    protected AbstractListAssert<?, List<? extends Tuple>, Tuple, ObjectAssert<Tuple>> assertThatItemsInTable() {
        return assertThat(dynamoDBClient.scan(new ScanRequest().withTableName(tableName)).getItems())
                .extracting(
                        Map::keySet,
                        map -> getStringAttribute(map, JOB_ID),
                        map -> getStringAttribute(map, UPDATE_TYPE),
                        map -> getStringAttribute(map, PARTITION_ID),
                        map -> getNumberAttribute(map, INPUT_FILES_COUNT),
                        map -> getStringAttribute(map, SPLIT_TO_PARTITION_IDS));
    }

    protected Tuple createCompactionItem(String jobId, int inputFilesCount, String partitionId) {
        return tuple(
                Stream.of(JOB_ID, UPDATE_TIME, UPDATE_TYPE, PARTITION_ID, INPUT_FILES_COUNT).collect(Collectors.toSet()),
                jobId, UPDATE_TYPE_CREATED, partitionId, "" + inputFilesCount, null);
    }

    protected Tuple createSplittingCompactionItem(String jobId, int inputFilesCount, String partitionId, String splitToPartitionIds) {
        return tuple(
                Stream.of(JOB_ID, UPDATE_TIME, UPDATE_TYPE, PARTITION_ID, INPUT_FILES_COUNT, SPLIT_TO_PARTITION_IDS).collect(Collectors.toSet()),
                jobId, UPDATE_TYPE_CREATED, partitionId, "" + inputFilesCount, splitToPartitionIds);
    }
}
