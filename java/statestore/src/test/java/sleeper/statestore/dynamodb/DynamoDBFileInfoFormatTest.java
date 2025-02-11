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

package sleeper.statestore.dynamodb;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import sleeper.core.statestore.FileInfo;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamoDBFileInfoFormatTest {
    private final String tableId = "test-table-id";
    private final DynamoDBFileInfoFormat fileInfoFormat = new DynamoDBFileInfoFormat(tableId);

    @Test
    void shouldCreateActiveFileRecord() {
        // Given
        FileInfo fileInfo = createActiveFile("file1.parquet", "partition1");

        // When / Then
        assertThat(fileInfoFormat.createActiveFileRecord(fileInfo))
                .isEqualTo(Map.of(
                        "PartitionIdAndFileName", new AttributeValue().withS("partition1|file1.parquet"),
                        "Status", new AttributeValue().withS("ACTIVE"),
                        "TableId", new AttributeValue().withS("test-table-id")
                ));
    }

    @Test
    void shouldCreateReadyForGCRecord() {
        // Given
        FileInfo fileInfo = createReadyForGCFile("file1.parquet", "partition1");

        // When / Then
        assertThat(fileInfoFormat.createReadyForGCRecord(fileInfo))
                .isEqualTo(Map.of(
                        "FileName", new AttributeValue().withS("file1.parquet"),
                        "PartitionId", new AttributeValue().withS("partition1"),
                        "Status", new AttributeValue().withS("READY_FOR_GARBAGE_COLLECTION"),
                        "TableId", new AttributeValue().withS("test-table-id")
                ));
    }

    @Test
    void shouldCreateFileInfoRecordBasedOnState() {
        // Given
        FileInfo activeFile = createActiveFile("file1.parquet", "partition1");
        FileInfo readyForGCFile = createReadyForGCFile("file2.parquet", "partition2");

        // When / Then
        assertThat(fileInfoFormat.createRecord(activeFile))
                .isEqualTo(Map.of(
                        "PartitionIdAndFileName", new AttributeValue().withS("partition1|file1.parquet"),
                        "Status", new AttributeValue().withS("ACTIVE"),
                        "TableId", new AttributeValue().withS("test-table-id")
                ));
        assertThat(fileInfoFormat.createRecord(readyForGCFile))
                .isEqualTo(Map.of(
                        "FileName", new AttributeValue().withS("file2.parquet"),
                        "PartitionId", new AttributeValue().withS("partition2"),
                        "Status", new AttributeValue().withS("READY_FOR_GARBAGE_COLLECTION"),
                        "TableId", new AttributeValue().withS("test-table-id")
                ));
    }

    @Test
    void shouldCreateHashAndSortKeyForActiveFile() {
        // Given
        FileInfo fileInfo = createActiveFile("file1.parquet", "partition1");

        // When / Then
        assertThat(fileInfoFormat.createActiveFileKey(fileInfo))
                .isEqualTo(Map.of(
                        "TableId", new AttributeValue().withS("test-table-id"),
                        "PartitionIdAndFileName", new AttributeValue().withS("partition1|file1.parquet")
                ));
    }

    @Test
    void shouldCreateHashAndSortKeyForReadyForGCFile() {
        // Given
        FileInfo fileInfo = createReadyForGCFile("file1.parquet", "partition1");

        // When / Then
        assertThat(fileInfoFormat.createReadyForGCKey(fileInfo))
                .isEqualTo(Map.of(
                        "TableId", new AttributeValue().withS("test-table-id"),
                        "FileName", new AttributeValue().withS("file1.parquet")
                ));
    }

    @Test
    void shouldCreateFileInfoFromActiveFileRecord() {
        // Given
        Map<String, AttributeValue> item = Map.of(
                "PartitionIdAndFileName", new AttributeValue().withS("partition1|file1.parquet"),
                "Status", new AttributeValue().withS("ACTIVE"),
                "TableId", new AttributeValue().withS("test-table-id")
        );

        // When / Then
        assertThat(fileInfoFormat.getFileInfoFromAttributeValues(item))
                .isEqualTo(FileInfo.builder()
                        .filename("file1.parquet")
                        .partitionId("partition1")
                        .fileStatus(FileInfo.FileStatus.ACTIVE)
                        .build());
    }

    private FileInfo createActiveFile(String fileName, String partitionId) {
        return createFile(fileName, partitionId, FileInfo.FileStatus.ACTIVE);
    }

    private FileInfo createReadyForGCFile(String fileName, String partitionId) {
        return createFile(fileName, partitionId, FileInfo.FileStatus.READY_FOR_GARBAGE_COLLECTION);
    }

    private FileInfo createFile(String fileName, String partitionId, FileInfo.FileStatus status) {
        return FileInfo.builder()
                .filename(fileName)
                .partitionId(partitionId)
                .fileStatus(status)
                .build();
    }
}
