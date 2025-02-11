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
package sleeper.cdk.stack;

import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.athena.CfnDataCatalog;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.Policy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.lambda.IFunction;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;

import sleeper.cdk.Utils;
import sleeper.cdk.jars.BuiltJar;
import sleeper.cdk.jars.BuiltJars;
import sleeper.cdk.jars.LambdaCode;
import sleeper.configuration.properties.instance.InstanceProperties;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static sleeper.configuration.properties.instance.AthenaProperty.ATHENA_COMPOSITE_HANDLER_CLASSES;
import static sleeper.configuration.properties.instance.AthenaProperty.ATHENA_COMPOSITE_HANDLER_MEMORY;
import static sleeper.configuration.properties.instance.AthenaProperty.ATHENA_COMPOSITE_HANDLER_TIMEOUT_IN_SECONDS;
import static sleeper.configuration.properties.instance.AthenaProperty.SPILL_BUCKET_AGE_OFF_IN_DAYS;
import static sleeper.configuration.properties.instance.CommonProperty.ACCOUNT;
import static sleeper.configuration.properties.instance.CommonProperty.ID;
import static sleeper.configuration.properties.instance.CommonProperty.LOG_RETENTION_IN_DAYS;
import static sleeper.configuration.properties.instance.CommonProperty.REGION;

@SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
public class AthenaStack extends NestedStack {
    public AthenaStack(Construct scope, String id, InstanceProperties instanceProperties, BuiltJars jars,
                       CoreStacks coreStacks) {
        super(scope, id);

        String instanceId = instanceProperties.get(ID);
        int logRetentionDays = instanceProperties.getInt(LOG_RETENTION_IN_DAYS);
        IBucket jarsBucket = Bucket.fromBucketName(this, "JarsBucket", jars.bucketName());
        LambdaCode jarCode = jars.lambdaCode(BuiltJar.ATHENA, jarsBucket);

        String bucketName = Utils.truncateTo64Characters(String.join("-", "sleeper",
                instanceProperties.get(ID).toLowerCase(Locale.ROOT), "spill-bucket"));

        Bucket spillBucket = Bucket.Builder.create(this, "SpillBucket")
                .bucketName(bucketName)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .autoDeleteObjects(true)
                .encryption(BucketEncryption.S3_MANAGED)
                .lifecycleRules(Lists.newArrayList(LifecycleRule.builder()
                        .expiration(Duration.days(instanceProperties.getInt(SPILL_BUCKET_AGE_OFF_IN_DAYS)))
                        .build()))
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Key spillMasterKey = Key.Builder.create(this, "SpillMasterKey")
                .description("Master key used by Sleeper instance " + instanceId + " to generate data keys. The data" +
                        " keys created are used to encrypt spilled data to S3 when communicating with Amazon Athena.")
                .enableKeyRotation(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .pendingWindow(Duration.days(7))
                .build();

        Map<String, String> env = Utils.createDefaultEnvironment(instanceProperties);
        env.put("spill_bucket", spillBucket.getBucketName());
        env.put("kms_key_id", spillMasterKey.getKeyId());

        Integer memory = instanceProperties.getInt(ATHENA_COMPOSITE_HANDLER_MEMORY);
        Integer timeout = instanceProperties.getInt(ATHENA_COMPOSITE_HANDLER_TIMEOUT_IN_SECONDS);
        List<String> handlerClasses = instanceProperties.getList(ATHENA_COMPOSITE_HANDLER_CLASSES);

        Policy listAllBucketsPolicy = Policy.Builder.create(this, "ListAllBucketsPolicy")
                .statements(Lists.newArrayList(PolicyStatement.Builder.create()
                        .resources(Lists.newArrayList("*"))
                        .actions(Lists.newArrayList("S3:ListAllMyBuckets"))
                        .build()))
                .build();

        Policy keyGenerationPolicy = Policy.Builder.create(this, "KeyGenerationPolicy")
                .statements(Lists.newArrayList(PolicyStatement.Builder.create()
                        .resources(Lists.newArrayList("*"))
                        .actions(Lists.newArrayList("kms:GenerateRandom"))
                        .build()))
                .build();

        Policy getAthenaQueryStatusPolicy = Policy.Builder.create(this, "GetAthenaQueryStatusPolicy")
                .statements(Lists.newArrayList(PolicyStatement.Builder.create()
                        .resources(Lists.newArrayList("arn:aws:athena:" + instanceProperties.get(REGION) + ":"
                                + instanceProperties.get(ACCOUNT) + ":workgroup/*"))
                        .actions(Lists.newArrayList("athena:getQueryExecution"))
                        .build()))
                .build();

        for (String className : handlerClasses) {
            IFunction handler = createConnector(className, instanceId, logRetentionDays, jarCode, env, memory, timeout);

            jarsBucket.grantRead(handler);

            coreStacks.grantReadTablesAndData(handler);
            spillBucket.grantReadWrite(handler);
            spillMasterKey.grant(handler, "kms:GenerateDataKey", "kms:DescribeKey");

            IRole role = Objects.requireNonNull(handler.getRole());
            // Required for when spill bucket changes
            role.attachInlinePolicy(listAllBucketsPolicy);

            // Required for when creating a encryption data key
            role.attachInlinePolicy(keyGenerationPolicy);

            // Allow our function to get the status of the query. Allowed to query all workgroups within this account
            // and region
            role.attachInlinePolicy(getAthenaQueryStatusPolicy);

        }

        Utils.addStackTagIfSet(this, instanceProperties);
    }

    private IFunction createConnector(String className, String instanceId, int logRetentionDays, LambdaCode jar, Map<String, String> env, Integer memory, Integer timeout) {
        String simpleClassName = className.substring(className.lastIndexOf(".") + 1);
        if (simpleClassName.endsWith("CompositeHandler")) {
            simpleClassName = simpleClassName.substring(0, simpleClassName.indexOf("CompositeHandler"));
        }

        String functionName = Utils.truncateTo64Characters(String.join("-", "sleeper",
                instanceId.toLowerCase(Locale.ROOT), simpleClassName, "athena-composite-handler"));

        IFunction athenaCompositeHandler = jar.buildFunction(this, simpleClassName + "AthenaCompositeHandler", builder -> builder
                .functionName(functionName)
                .memorySize(memory)
                .timeout(Duration.seconds(timeout))
                .runtime(Runtime.JAVA_11)
                .logRetention(Utils.getRetentionDays(logRetentionDays))
                .handler(className)
                .environment(env));

        CfnDataCatalog.Builder.create(this, simpleClassName + "AthenaDataCatalog")
                .name(instanceId + simpleClassName + "SleeperConnector")
                .description("Athena Connector for " + instanceId)
                .type("LAMBDA")
                .parameters(Map.of("function", athenaCompositeHandler.getFunctionArn()))
                .build();

        return athenaCompositeHandler;
    }
}
