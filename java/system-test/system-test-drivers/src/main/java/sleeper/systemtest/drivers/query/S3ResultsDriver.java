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

package sleeper.systemtest.drivers.query;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import sleeper.core.record.Record;
import sleeper.io.parquet.record.ParquetRecordReader;
import sleeper.systemtest.drivers.instance.SleeperInstanceContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static sleeper.configuration.properties.instance.SystemDefinedInstanceProperty.QUERY_RESULTS_BUCKET;

public class S3ResultsDriver {
    private final SleeperInstanceContext instance;
    private final AmazonS3 s3;

    public S3ResultsDriver(SleeperInstanceContext instance, AmazonS3 s3) {
        this.instance = instance;
        this.s3 = s3;
    }

    public Stream<Record> results(String queryId) {
        return s3.listObjects(instance.getInstanceProperties().get(QUERY_RESULTS_BUCKET), "query-" + queryId)
                .getObjectSummaries().stream()
                .flatMap(this::getRecords);
    }

    private Stream<Record> getRecords(S3ObjectSummary s3ObjectSummary) {
        String path = "s3a://" + s3ObjectSummary.getBucketName() + "/" + s3ObjectSummary.getKey();
        List<Record> records = new ArrayList<>();
        try {
            ParquetRecordReader reader = new ParquetRecordReader(new org.apache.hadoop.fs.Path(path),
                    instance.getTableProperties().getSchema());

            Record record = reader.read();
            while (null != record) {
                records.add(new Record(record));
                record = reader.read();
            }
            reader.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return records.stream();
    }
}
