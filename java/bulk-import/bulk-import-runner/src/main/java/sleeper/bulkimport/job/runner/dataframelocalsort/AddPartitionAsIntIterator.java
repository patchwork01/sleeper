/*
 * Copyright 2023 Crown Copyright
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

import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;

import sleeper.core.key.Key;
import sleeper.core.partition.Partition;
import sleeper.core.partition.PartitionTree;
import sleeper.core.schema.Schema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * An {@link Iterator} of {@link Row}s that takes an existing {@link Iterator}
 * of {@link Row}s and adds an integer id of the partition the key from each {@link Row}.
 * The integer id is determined by taking the leaf partitions, sorting them by their id,
 * and then assigning integers 0,1,...,numLeafPartitions -1 to them. This guarantees
 * that two {@link Row}s from the same leaf partition processed in different tasks/executors
 * will be get the same integer id.
 */
public class AddPartitionAsIntIterator implements Iterator<Row> {
    private final Iterator<Row> input;
    private final PartitionTree partitionTree;
    private final Map<String, Integer> partitionIdToInt;
    private final int numRowKeyFields;
    private final int numFields;

    public AddPartitionAsIntIterator(Iterator<Row> input, Schema schema, PartitionTree partitionTree) {
        this.input = input;
        this.partitionTree = partitionTree;

        // Sort the leaf partitions by id so that we can create a mapping from partition id to
        // int in a way that is consistent across multiple calls to this function across different
        // executors in the same Spark job.
        SortedSet<String> sortedLeafPartitionIds = new TreeSet<>();
        this.partitionTree.getAllPartitions().stream()
                .filter(Partition::isLeafPartition)
                .map(Partition::getId)
                .forEach(sortedLeafPartitionIds::add);
        this.partitionIdToInt = new TreeMap<>();
        int i = 0;
        for (String leafPartitionId : sortedLeafPartitionIds) {
            partitionIdToInt.put(leafPartitionId, i);
            i++;
        }
        this.numRowKeyFields = schema.getRowKeyFieldNames().size();
        this.numFields = schema.getAllFieldNames().size();
    }

    @Override
    public boolean hasNext() {
        return input.hasNext();
    }

    @Override
    public Row next() {
        Row row = input.next();

        Object[] rowWithPartition = new Object[numFields + 1];
        List<Object> key = new ArrayList<>(numRowKeyFields);
        for (int i = 0; i < numFields; i++) {
            rowWithPartition[i] = row.get(i);
            if (i < numRowKeyFields) {
                key.add(rowWithPartition[i]);
            }
        }

        String partitionId = partitionTree.getLeafPartition(Key.create(key)).getId();
        rowWithPartition[numFields] = partitionIdToInt.get(partitionId);

        return RowFactory.create(rowWithPartition);
    }
}
