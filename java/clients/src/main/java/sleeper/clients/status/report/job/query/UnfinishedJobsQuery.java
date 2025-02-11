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
package sleeper.clients.status.report.job.query;

import sleeper.compaction.job.CompactionJobStatusStore;
import sleeper.compaction.job.status.CompactionJobStatus;
import sleeper.core.table.TableIdentity;
import sleeper.ingest.job.status.IngestJobStatus;
import sleeper.ingest.job.status.IngestJobStatusStore;

import java.util.List;

public class UnfinishedJobsQuery implements JobQuery {
    private final TableIdentity tableId;

    public UnfinishedJobsQuery(TableIdentity tableId) {
        this.tableId = tableId;
    }

    @Override
    public List<CompactionJobStatus> run(CompactionJobStatusStore statusStore) {
        return statusStore.getUnfinishedJobs(tableId);
    }

    @Override
    public List<IngestJobStatus> run(IngestJobStatusStore statusStore) {
        return statusStore.getUnfinishedJobs(tableId);
    }

    @Override
    public Type getType() {
        return Type.UNFINISHED;
    }
}
