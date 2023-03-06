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
package sleeper.configuration.properties;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;

/**
 * Properties set internally by sleeper and not by the user. These are set by the system itself at deployment time
 * so require no default or validation. Even if you set these in your properties file, they will be overwritten.
 */
// Suppress as this class will always be referenced before impl class, so initialization behaviour will be deterministic
@SuppressFBWarnings("IC_SUPERCLASS_USES_SUBCLASS_DURING_INITIALIZATION")
public interface SystemDefinedInstanceProperty extends InstanceProperty {
    SystemDefinedInstanceProperty VERSION = named("sleeper.version")
            .description("The version of Sleeper that is being used. This property is used to identify the correct jars in the S3JarsBucket and to " +
                    "select the correct tag in the ECR repositories.")
            .propertyGroup(InstancePropertyGroup.COMMON).build();

    // Configuration
    SystemDefinedInstanceProperty CONFIG_BUCKET = named("sleeper.config.bucket")
            .description("The S3 bucket name used to store configuration files.")
            .propertyGroup(InstancePropertyGroup.COMMON)
            .build();

    // Table metrics
    SystemDefinedInstanceProperty TABLE_METRICS_RULES = named("sleeper.table.metrics.rulenames")
            .description("The names of the CloudWatch rules that trigger generation of metrics for tables.")
            .propertyGroup(InstancePropertyGroup.COMMON)
            .build();

    // Query
    SystemDefinedInstanceProperty QUERY_LAMBDA_ROLE = named("sleeper.query.role.arn")
            .description("The ARN for the role with required permissions to query sleeper.")
            .propertyGroup(InstancePropertyGroup.QUERY)
            .build();
    SystemDefinedInstanceProperty QUERY_WEBSOCKET_API_URL = named("sleeper.query.websocket.api.url")
            .description("The URL of the WebSocket API for querying sleeper.")
            .propertyGroup(InstancePropertyGroup.QUERY)
            .build();
    SystemDefinedInstanceProperty QUERY_QUEUE_URL = named("sleeper.query.queue.url")
            .description("The URL of the queue responsible for sending a query to sleeper.")
            .propertyGroup(InstancePropertyGroup.QUERY)
            .build();
    SystemDefinedInstanceProperty QUERY_DLQ_URL = named("sleeper.query.dlq.url")
            .description("The URL of the dead letter queue used when querying sleeper.")
            .propertyGroup(InstancePropertyGroup.QUERY)
            .build();
    SystemDefinedInstanceProperty QUERY_RESULTS_QUEUE_URL = named("sleeper.query.results.queue.url")
            .description("The URL of the queue responsible for retrieving results from a query sent to sleeper.")
            .propertyGroup(InstancePropertyGroup.QUERY)
            .build();
    SystemDefinedInstanceProperty QUERY_RESULTS_BUCKET = named("sleeper.query.results.bucket")
            .description("The S3 Bucket name of the query results bucket.")
            .propertyGroup(InstancePropertyGroup.QUERY)
            .build();
    SystemDefinedInstanceProperty QUERY_TRACKER_TABLE_NAME = named("sleeper.query.tracker.table.name")
            .description("The name of the table responsible for tracking query progress.")
            .propertyGroup(InstancePropertyGroup.QUERY)
            .build();

    // Compactions
    SystemDefinedInstanceProperty COMPACTION_CLUSTER = named("sleeper.compaction.cluster")
            .description("The name of the cluster used for compactions.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty SPLITTING_COMPACTION_CLUSTER = named("sleeper.compaction.splitting.cluster")
            .description("The name of the cluster used for splitting compactions.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_TASK_EC2_DEFINITION_FAMILY = named("sleeper.compaction.ec2.task.definition")
            .description("The name of the family of EC2 task definitions used for compactions.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_TASK_FARGATE_DEFINITION_FAMILY = named("sleeper.compaction.fargate.task.definition")
            .description("The name of the family of Fargate task definitions used for compactions.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_JOB_CREATION_LAMBDA_FUNCTION = named("sleeper.compaction.job.creation.lambda.function")
            .description("The function name of the compaction job creation lambda.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_JOB_CREATION_CLOUDWATCH_RULE = named("sleeper.compaction.job.creation.rule")
            .description("The name of the CloudWatch rule that periodically triggers the compaction job creation lambda.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_JOB_QUEUE_URL = named("sleeper.compaction.job.queue.url")
            .description("The URL of the queue for compaction jobs.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_JOB_DLQ_URL = named("sleeper.compaction.job.dlq.url")
            .description("The URL of the dead letter queue for compaction jobs.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_TASK_CREATION_LAMBDA_FUNCTION = named("sleeper.compaction.task.creation.lambda.function")
            .description("The function name of the compaction task creation lambda.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_TASK_CREATION_CLOUDWATCH_RULE = named("sleeper.compaction.task.creation.rule")
            .description("The name of the CloudWatch rule that periodically triggers the compaction task creation lambda.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty COMPACTION_AUTO_SCALING_GROUP = named("sleeper.compaction.scaling.group")
            .description("The name of the compaction EC2 auto scaling group.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty SPLITTING_COMPACTION_TASK_EC2_DEFINITION_FAMILY = named("sleeper.compaction.splitting.ec2.task.definition")
            .description("The name of the family of EC2 task definitions used for splitting compactions.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty SPLITTING_COMPACTION_TASK_FARGATE_DEFINITION_FAMILY = named("sleeper.compaction.splitting.fargate.task.definition")
            .description("The name of the family of Fargate task definitions used for splitting compactions.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();

    SystemDefinedInstanceProperty SPLITTING_COMPACTION_JOB_QUEUE_URL = named("sleeper.compaction.splitting.job.queue.url")
            .description("The URL of the queue for splitting compaction jobs.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty SPLITTING_COMPACTION_JOB_DLQ_URL = named("sleeper.compaction.splitting.job.dlq.url")
            .description("The URL of the dead letter queue for splitting compaction jobs.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty SPLITTING_COMPACTION_TASK_CREATION_LAMBDA_FUNCTION = named("sleeper.compaction.splitting.task.creation.lambda.function")
            .description("The function name of the splitting compaction task creation lambda.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty SPLITTING_COMPACTION_TASK_CREATION_CLOUDWATCH_RULE = named("sleeper.compaction.splitting.task.creation.rule")
            .description("The name of the CloudWatch rule that periodically triggers the splitting compaction task creation lambda.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();
    SystemDefinedInstanceProperty SPLITTING_COMPACTION_AUTO_SCALING_GROUP = named("sleeper.compaction.splitting.scaling.group")
            .description("The name of the splitting compaction EC2 auto scaling group.")
            .propertyGroup(InstancePropertyGroup.COMPACTION)
            .build();

    // Partition splitting
    SystemDefinedInstanceProperty PARTITION_SPLITTING_QUEUE_URL = named("sleeper.partition.splitting.queue.url")
            .description("The URL of the queue for partition splitting.")
            .propertyGroup(InstancePropertyGroup.PARTITION_SPLITTING)
            .build();
    SystemDefinedInstanceProperty PARTITION_SPLITTING_DLQ_URL = named("sleeper.partition.splitting.dlq.url")
            .description("The URL of the dead letter queue for partition splitting.")
            .propertyGroup(InstancePropertyGroup.PARTITION_SPLITTING)
            .build();
    SystemDefinedInstanceProperty PARTITION_SPLITTING_LAMBDA_FUNCTION = named("sleeper.partition.splitting.lambda.function")
            .description("The function name of the partition splitting lambda.")
            .propertyGroup(InstancePropertyGroup.PARTITION_SPLITTING)
            .build();
    SystemDefinedInstanceProperty PARTITION_SPLITTING_CLOUDWATCH_RULE = named("sleeper.partition.splitting.rule")
            .description("The name of the CloudWatch rule that periodically triggers the partition splitting lambda.")
            .propertyGroup(InstancePropertyGroup.PARTITION_SPLITTING)
            .build();

    // Garbage collection
    SystemDefinedInstanceProperty GARBAGE_COLLECTOR_CLOUDWATCH_RULE = named("sleeper.gc.rule")
            .description("The name of the CloudWatch rule that periodically triggers the garbage collector lambda.")
            .propertyGroup(InstancePropertyGroup.GARBAGE_COLLECTOR)
            .build();

    // Ingest
    SystemDefinedInstanceProperty INGEST_LAMBDA_FUNCTION = named("sleeper.ingest.lambda.function")
            .description("The function name of the ingest task creator lambda.")
            .propertyGroup(InstancePropertyGroup.INGEST)
            .build();
    SystemDefinedInstanceProperty INGEST_CLOUDWATCH_RULE = named("sleeper.ingest.rule")
            .description("The name of the CloudWatch rule that periodically triggers the ingest task creator lambda.")
            .propertyGroup(InstancePropertyGroup.INGEST)
            .build();
    SystemDefinedInstanceProperty INGEST_JOB_QUEUE_URL = named("sleeper.ingest.job.queue.url")
            .description("The URL of the queue for ingest jobs.")
            .propertyGroup(InstancePropertyGroup.INGEST)
            .build();
    SystemDefinedInstanceProperty INGEST_JOB_DLQ_URL = named("sleeper.ingest.job.dlq.url")
            .description("The URL of the dead letter queue for ingest jobs.")
            .propertyGroup(InstancePropertyGroup.INGEST)
            .build();
    SystemDefinedInstanceProperty INGEST_TASK_DEFINITION_FAMILY = named("sleeper.ingest.task.definition.family")
            .description("The name of the family of task definitions used for ingest tasks.")
            .propertyGroup(InstancePropertyGroup.INGEST)
            .build();
    SystemDefinedInstanceProperty INGEST_CLUSTER = named("sleeper.ingest.cluster")
            .description("The name of the cluster used for ingest.")
            .propertyGroup(InstancePropertyGroup.INGEST)
            .build();

    // Bulk import
    SystemDefinedInstanceProperty BULK_IMPORT_BUCKET = named("sleeper.bulk.import.bucket")
            .description("The S3 Bucket name of the bulk import bucket.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();

    // Bulk import using EMR - these properties are used by both the persistent
    // and non-persistent EMR stacks
    SystemDefinedInstanceProperty BULK_IMPORT_EMR_EC2_ROLE_NAME = named("sleeper.bulk.import.emr.ec2.role.name")
            .description("The name of the job flow role for bulk import using EMR.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();
    SystemDefinedInstanceProperty BULK_IMPORT_EMR_CLUSTER_ROLE_NAME = named("sleeper.bulk.import.emr.role.name")
            .description("The name of the role assumed by the bulk import clusters using EMR.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();
    SystemDefinedInstanceProperty BULK_IMPORT_EMR_SECURITY_CONF_NAME = named("sleeper.bulk.import.emr.security.conf.name")
            .description("The name of the security configuration used by bulk import using EMR.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();

    // Bulk import using EMR
    SystemDefinedInstanceProperty BULK_IMPORT_EMR_JOB_QUEUE_URL = named("sleeper.bulk.import.emr.job.queue.url")
            .description("The URL of the queue for bulk import jobs using EMR.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();

    // Bulk import using persistent EMR
    SystemDefinedInstanceProperty BULK_IMPORT_PERSISTENT_EMR_JOB_QUEUE_URL = named("sleeper.bulk.import.persistent.emr.job.queue.url")
            .description("The URL of the queue for bulk import jobs using persistent EMR.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();
    SystemDefinedInstanceProperty BULK_IMPORT_PERSISTENT_EMR_MASTER_DNS = named("sleeper.bulk.import.persistent.emr.master")
            .description("The public DNS name of the cluster's master node for bulk import using persistent EMR.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();

    // Bulk import using EKS
    SystemDefinedInstanceProperty BULK_IMPORT_EKS_JOB_QUEUE_URL = named("sleeper.bulk.import.eks.job.queue.url")
            .description("The URL of the queue for bulk import jobs using EKS.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();
    SystemDefinedInstanceProperty BULK_IMPORT_EKS_STATE_MACHINE_ARN = named("sleeper.bulk.import.eks.statemachine.arn")
            .description("The ARN of the state machine for bulk import jobs using EKS.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();
    SystemDefinedInstanceProperty BULK_IMPORT_EKS_NAMESPACE = named("sleeper.bulk.import.eks.k8s.namespace")
            .description("The namespace ID of the bulk import cluster using EKS.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();
    SystemDefinedInstanceProperty BULK_IMPORT_EKS_CLUSTER_ENDPOINT = named("sleeper.bulk.import.eks.k8s.endpoint")
            .description("The endpoint of the bulk import cluster using EKS.")
            .propertyGroup(InstancePropertyGroup.BULK_IMPORT)
            .build();

    static List<SystemDefinedInstanceProperty> getAll() {
        return Index.INSTANCE.getAll();
    }

    static boolean has(String propertyName) {
        return Index.INSTANCE.getByName(propertyName).isPresent();
    }

    @Override
    default boolean isSystemDefined() {
        return true;
    }

    static SystemDefinedInstancePropertyImpl.Builder named(String propertyName) {
        return SystemDefinedInstancePropertyImpl.named(propertyName)
                .addToIndex(Index.INSTANCE::add);
    }

    class Index {
        private Index() {
        }

        private static final SleeperPropertyIndex<SystemDefinedInstanceProperty> INSTANCE = new SleeperPropertyIndex<>();
    }
}
