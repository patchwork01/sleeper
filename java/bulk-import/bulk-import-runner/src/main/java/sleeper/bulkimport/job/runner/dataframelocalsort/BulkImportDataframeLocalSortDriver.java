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
package sleeper.bulkimport.job.runner.dataframelocalsort;

import com.google.common.collect.Lists;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.encoders.RowEncoder;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sleeper.bulkimport.job.runner.BulkImportJobDriver;
import sleeper.bulkimport.job.runner.BulkImportJobInput;
import sleeper.bulkimport.job.runner.BulkImportJobRunner;
import sleeper.bulkimport.job.runner.SparkFileInfoRow;
import sleeper.bulkimport.job.runner.StructTypeFactory;
import sleeper.bulkimport.job.runner.rdd.WriteParquetFile;
import sleeper.core.partition.Partition;
import sleeper.core.schema.Schema;
import sleeper.core.schema.SchemaSerDe;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * This class runs {@link BulkImportJobDriver} with a {@link BulkImportJobRunner} which
 * uses Spark's Dataframe API to efficiently sort and write out the data split by
 * Sleeper partition.
 */
public class BulkImportDataframeLocalSortDriver {
    private static final Logger LOGGER = LoggerFactory.getLogger(BulkImportDataframeLocalSortDriver.class);
    private static final String PARTITION_FIELD_NAME = "__partition";

    private BulkImportDataframeLocalSortDriver() {
    }

    public static void main(String[] args) throws Exception {
        BulkImportJobDriver.start(args, BulkImportDataframeLocalSortDriver::createFileInfos);
    }

    public static Dataset<Row> createFileInfos(BulkImportJobInput input) {
        Schema schema = input.schema();
        String schemaAsString = new SchemaSerDe().toJson(schema);
        StructType convertedSchema = new StructTypeFactory().getStructType(schema);
        StructType schemaWithPartitionField = createEnhancedSchema(convertedSchema);

        int numLeafPartitions = (int) input.broadcastedPartitions().value()
                .stream().filter(Partition::isLeafPartition).count();
        LOGGER.info("There are {} leaf partitions", numLeafPartitions);

        Dataset<Row> dataWithPartition = input.rows().mapPartitions(
                new AddPartitionAsIntFunction(schemaAsString, input.broadcastedPartitions()),
                RowEncoder.apply(schemaWithPartitionField));
        LOGGER.info("After adding partition id as int, there are {} partitions", dataWithPartition.rdd().getNumPartitions());

        Dataset<Row> repartitionedData = new com.joom.spark.package$implicits$ExplicitRepartitionWrapper(dataWithPartition)
                .explicitRepartition(numLeafPartitions, new Column(PARTITION_FIELD_NAME));
        LOGGER.info("After repartitioning data, there are {} partitions", repartitionedData.rdd().getNumPartitions());

        Column[] sortColumns = Lists.newArrayList(schema.getRowKeyFieldNames(), schema.getSortKeyFieldNames())
                .stream()
                .flatMap(Collection::stream)
                .map(Column::new)
                .toArray(Column[]::new);
        LOGGER.info("Sorting by columns {}", Arrays.stream(sortColumns)
                .map(Column::toString)
                .collect(Collectors.joining(",")));

        Dataset<Row> sortedRows = repartitionedData.sortWithinPartitions(sortColumns);
        LOGGER.info("There are {} partitions in the sorted-within-partition Dataset", sortedRows.rdd().getNumPartitions());

        return sortedRows.mapPartitions(
                new WriteParquetFile(
                        input.instanceProperties().saveAsString(),
                        input.tableProperties().saveAsString(),
                        input.conf(), input.broadcastedPartitions()),
                RowEncoder.apply(SparkFileInfoRow.createFileInfoSchema()));
    }

    private static StructType createEnhancedSchema(StructType convertedSchema) {
        StructType structTypeWithPartition = new StructType(convertedSchema.fields());
        return structTypeWithPartition
                .add(new StructField(PARTITION_FIELD_NAME, DataTypes.IntegerType, false, null));
    }
}
