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
package sleeper.systemtest.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.EnumUtils;

import sleeper.configuration.Utils;
import sleeper.configuration.properties.SleeperPropertyIndex;
import sleeper.configuration.properties.instance.InstanceProperty;

import java.util.List;
import java.util.Objects;

// Suppress as this class will always be referenced before impl class, so initialization behaviour will be deterministic
@SuppressFBWarnings("IC_SUPERCLASS_USES_SUBCLASS_DURING_INITIALIZATION")
public interface SystemTestProperty extends InstanceProperty {
    SystemTestProperty SYSTEM_TEST_ID = Index.propertyBuilder("sleeper.systemtest.standalone.id")
            .description("The id of the deployment, when deploying standalone.")
            .editable(false).build();
    SystemTestProperty SYSTEM_TEST_ACCOUNT = Index.propertyBuilder("sleeper.systemtest.standalone.account")
            .description("The AWS account when deploying standalone.")
            .editable(false).build();
    SystemTestProperty SYSTEM_TEST_REGION = Index.propertyBuilder("sleeper.systemtest.standalone.region")
            .description("The AWS region when deploying standalone.")
            .editable(false).build();
    SystemTestProperty SYSTEM_TEST_VPC_ID = Index.propertyBuilder("sleeper.systemtest.standalone.vpc")
            .description("The id of the VPC to deploy to, when deploying standalone.")
            .editable(false).build();
    SystemTestProperty SYSTEM_TEST_JARS_BUCKET = Index.propertyBuilder("sleeper.systemtest.standalone.jars.bucket")
            .description("The S3 bucket containing the jar files of the Sleeper components, when deploying standalone.")
            .runCdkDeployWhenChanged(true).build();
    SystemTestProperty SYSTEM_TEST_REPO = Index.propertyBuilder("sleeper.systemtest.repo")
            .description("The image in ECR used for writing random data to the system")
            .validationPredicate(Objects::nonNull)
            .runCdkDeployWhenChanged(true).build();
    SystemTestProperty SYSTEM_TEST_CLUSTER_ENABLED = Index.propertyBuilder("sleeper.systemtest.cluster.enabled")
            .description("Whether to deploy the system test cluster for data generation")
            .defaultValue("true").validationPredicate(Utils::isTrueOrFalse)
            .runCdkDeployWhenChanged(true).build();
    SystemTestProperty SYSTEM_TEST_CLUSTER_NAME = Index.propertyBuilder("sleeper.systemtest.cluster")
            .description("The name of the ECS cluster where system test tasks will run")
            .setByCdk(true).build();
    SystemTestProperty SYSTEM_TEST_BUCKET_NAME = Index.propertyBuilder("sleeper.systemtest.bucket")
            .description("The name of the bucket where system test data will be stored")
            .setByCdk(true).build();
    SystemTestProperty WRITE_DATA_TASK_DEFINITION_FAMILY = Index.propertyBuilder("sleeper.systemtest.task.definition")
            .description("The name of the family of task definitions used for writing data")
            .setByCdk(true).build();
    SystemTestProperty WRITE_DATA_ROLE_NAME = Index.propertyBuilder("sleeper.systemtest.writer.role")
            .description("The name of the role used when writing data for an instance in an ECS cluster")
            .setByCdk(true).build();
    SystemTestProperty SYSTEM_TEST_TASK_CPU = Index.propertyBuilder("sleeper.systemtest.task.cpu")
            .description("The number of CPU units for the containers that write random data, where 1024 is 1 vCPU.\n" +
                    "For valid values, see: " +
                    "https://docs.aws.amazon.com/AmazonECS/latest/userguide/fargate-task-defs.html")
            .defaultValue("1024").runCdkDeployWhenChanged(true).build();
    SystemTestProperty SYSTEM_TEST_TASK_MEMORY = Index.propertyBuilder("sleeper.systemtest.task.memory.mb")
            .description("The amount of memory for the containers that write random data, in MiB.\n" +
                    "For valid values, see: " +
                    "https://docs.aws.amazon.com/AmazonECS/latest/userguide/fargate-task-defs.html")
            .defaultValue("4096").runCdkDeployWhenChanged(true).build();
    SystemTestProperty INGEST_MODE = Index.propertyBuilder("sleeper.systemtest.ingest.mode")
            .description("The ingest mode to write random data. This should be either 'direct', 'queue', or 'generate_only'.\n" +
                    "'Direct' means that the data is written directly using an ingest coordinator.\n" +
                    "'Queue' means that the data is written to a Parquet file and an ingest job is created " +
                    "and posted to the ingest queue.\n" +
                    "'Generate_only' means that the data is written to a Parquet file in the table data bucket, " +
                    "but the file is not ingested. The ingest will have to be performed manually in a seperate step.")
            .defaultValue(IngestMode.DIRECT.toString())
            .validationPredicate(s -> EnumUtils.isValidEnumIgnoreCase(IngestMode.class, s)).build();
    SystemTestProperty NUMBER_OF_WRITERS = Index.propertyBuilder("sleeper.systemtest.writers")
            .description("The number of containers that write random data")
            .defaultValue("1").validationPredicate(Utils::isPositiveInteger).build();
    SystemTestProperty NUMBER_OF_RECORDS_PER_WRITER = Index.propertyBuilder("sleeper.systemtest.records.per.writer")
            .description("The number of random records that each container should write")
            .defaultValue("100").validationPredicate(Utils::isPositiveInteger).build();
    SystemTestProperty MIN_RANDOM_INT = Index.propertyBuilder("sleeper.systemtest.random.int.min")
            .description("The minimum value of integers generated randomly during random record generation")
            .defaultValue("0").validationPredicate(Utils::isInteger).build();
    SystemTestProperty MAX_RANDOM_INT = Index.propertyBuilder("sleeper.systemtest.random.int.max")
            .description("The maximum value of integers generated randomly during random record generation")
            .defaultValue("100000000").validationPredicate(Utils::isInteger).build();
    SystemTestProperty MIN_RANDOM_LONG = Index.propertyBuilder("sleeper.systemtest.random.long.min")
            .description("The minimum value of longs generated randomly during random record generation")
            .defaultValue("0").validationPredicate(Utils::isLong).build();
    SystemTestProperty MAX_RANDOM_LONG = Index.propertyBuilder("sleeper.systemtest.random.long.max")
            .description("The maximum value of longs generated randomly during random record generation")
            .defaultValue("10000000000").validationPredicate(Utils::isLong).build();
    SystemTestProperty RANDOM_STRING_LENGTH = Index.propertyBuilder("sleeper.systemtest.random.string.length")
            .description("The length of strings generated randomly during random record generation")
            .defaultValue("10").validationPredicate(Utils::isNonNegativeInteger).build();
    SystemTestProperty RANDOM_BYTE_ARRAY_LENGTH = Index.propertyBuilder("sleeper.systemtest.random.bytearray.length")
            .description("The length of byte arrays generated randomly during random record generation")
            .defaultValue("10").validationPredicate(Utils::isNonNegativeInteger).build();
    SystemTestProperty MAX_ENTRIES_RANDOM_MAP = Index.propertyBuilder("sleeper.systemtest.random.map.length")
            .description("The maximum number of entries in maps generated randomly during random record generation\n" +
                    "(the number of entries in the map will range randomly from 0 to this number)")
            .defaultValue("10").validationPredicate(Utils::isNonNegativeInteger).build();
    SystemTestProperty MAX_ENTRIES_RANDOM_LIST = Index.propertyBuilder("sleeper.systemtest.random.list.length")
            .description("The maximum number of entries in lists generated randomly during random record generation\n" +
                    "(the number of entries in the list will range randomly from 0 to this number)")
            .defaultValue("10").validationPredicate(Utils::isNonNegativeInteger).build();

    static List<SystemTestProperty> getAll() {
        return Index.INSTANCE.getAll();
    }

    class Index {
        private Index() {
        }

        static final SleeperPropertyIndex<SystemTestProperty> INSTANCE = new SleeperPropertyIndex<>();

        private static SystemTestPropertyImpl.Builder propertyBuilder(String propertyName) {
            return SystemTestPropertyImpl.named(propertyName)
                    .addToIndex(INSTANCE::add);
        }
    }
}
