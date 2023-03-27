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
package sleeper.job.common;

import com.amazonaws.services.sqs.model.QueueDoesNotExistException;

import java.util.Map;
import java.util.Optional;

public class QueueMessageCountInMemoryClient implements QueueMessageCount.Client {

    private final Map<String, QueueMessageCount> countByQueueName;

    private QueueMessageCountInMemoryClient(Map<String, QueueMessageCount> countByQueueName) {
        this.countByQueueName = countByQueueName;
    }

    public static QueueMessageCountInMemoryClient from(Map<String, QueueMessageCount> countByQueueName) {
        return new QueueMessageCountInMemoryClient(countByQueueName);
    }

    @Override
    public QueueMessageCount getQueueMessageCount(String queueUrl) {
        return Optional.ofNullable(countByQueueName.get(queueUrl))
                .orElseThrow(() -> new QueueDoesNotExistException("Queue does not exist: " + queueUrl));
    }
}
