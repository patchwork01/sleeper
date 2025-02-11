Releases
=========

This page documents the releases of Sleeper. Performance figures for each release
are available [here](docs/13-system-tests.md#performance-benchmarks)

## Version 0.20.0

*Note: this release contains breaking changes. It is not possible to upgrade from a previous version of Sleeper
to version 0.20.0*

This contains the following improvements:

Tables:

- Tables are now internally referenced by a unique ID assigned upon creation. This is in preparation for
  adding the ability to rename tables in the future.
- Improved support for lots of tables in compaction and ingest status stores by updating the hash key of
  the DynamoDB tables.
- Table related infrastructure is now shared between all tables. The following resources are now only deployed once:
    - Table data bucket.
    - Table metrics lambda.
    - State store.
- Table initialisation is no longer performed by CDK.
    - A new client class `AddTable` is now responsible for initialising tables.
- Added configurable timeout property for `TablePropertiesProvider`.

State store:

- The default state store has been updated to the `S3StateStore`.
- The `minRowKey`, `maxRowKey`, and `rowKeyTypes` fields have been removed from the `FileInfo` class.

Ingest:

- Added instance property to allow setting the S3 upload block size.

Bulk import:

- Added support for overriding spark configuration and platform specification in EMR serverless jobs.
- Added support for setting the initial capacity of the EMR serverless application.
- Added support for enabling EMR Studio by using the optional stack `EmrStudioStack`.

Clients:

- The admin client now respects the `EDITOR` environment variable when updating properties.
- Adding an optional stack in the admin client now uploads docker images if the new stack requires one.

Query:

- Validation failures for queries are now recorded in the `DynamoDBQueryTracker`.
- Added client to view status of query tracker in `scripts/utility/queryTrackerReport.sh`.
- Removed inheritance relationship between `Query` and `LeafPartitionQuery`.

Tests:

- Added system tests for using the `S3StateStore`.
- System tests now purge relevant SQS queues if a test fails.
- Improved performance of `ingest-runner` module tests.
- Added system tests with many tables in one instance.

Bugfixes:

- Fixed an issue where the python API would not generate unique IDs for each query.
- Fixed an issue where the instance ID length was not being validated correctly.
- Fixed an issue where trying to bulk import using EMR serverless to a table using `S3StateStore` would
  throw a `NullPointerException`.
- Fixed an issue where sending an ingest job with a null file would not report the job as invalid.
- Fixed an issue where the role assumed by tasks in the system test data generation cluster exceeded the maximum size.
- Fixed an issue where the CDK deployment would fail if an ingest source bucket was not set.
- Fixed a conflict between temporary directory paths used by the CLI.

## Version 0.19.0

This contains the following improvements:

Bulk Import:

- Use official Spark Docker image for bulk import in an EKS Kubernetes cluster
- Added support for EMR Serverless in the ingest batcher

Deployment:

- Added ability to deploy to Docker with LocalStack

Tests:

- Converted all system tests to run through JUnit-based DSL
- Added performance test for ingest
- Added functional system tests for
    - Ingest
    - Queries, SQS and direct
    - Bulk import via persistent EMR cluster
    - Python API
- Added Maven site reports to nightly system test output
- Improved output of Sleeper reports in nightly system tests
- Allow running either functional or performance test suite in nightly system tests
- Improved integration test coverage of ingest and partition splitting

Documentation:

- Documented system tests
- Updated deployment guide
- Updated getting started guide
- Updated release process

Misc:

- Added a utility script to show Parquet file page indexes
- Upgraded to use EMR version 6.13.0 and Spark 3.4.1
- Logging improvements in ingest, compaction, ingest batcher
- Use Python conventions for non-release version numbers in Python code

Bugfixes:

- Ingest batcher can now include any number of files in a batch
- Setting a property to an empty string will now behave the same as not setting it at all
- Admin client can now load properties which were set to an invalid value, and fix them
- Made reports output correctly based on query type when the query type was prompted
- Improved teardown of EMR Serverless to properly stop jobs and application before invoking CDK
- Scripts no longer fail when CDPATH variable is set
- Stopped building unused fat jars
- Stopped tear down and admin client failing when generated directory does not exist
- Prune Docker system in nightly tests to avoid disk filling up with images

## Version 0.18.0

This contains the following improvements:

Bulk Import:

- Support use of EMR serverless.
- Support use of Graviton instances in bulk import EMR.
- Report on validation status for standard ingest jobs and bulk import jobs.
- Added option to query rejected jobs in the `IngestJobStatusReport`.
- Support use of instance fleets in bulk import EMR.
- Updated default x86 instance types to use m6i equivalents.

Build:

- Converted GitHub Actions to run on pull requests from forks.
- Use Maven site to generate HTML reports on tests and linting failures.

Environment:

- Allow multiple users to access cdk-environment EC2.
- Allow users to load the configuration for an existing deployed environment without redeploying.

Deployment:

- Added ability to deploy to multiple subnets.
- Split properties templating from the deployment process, allowing you to specify your own configuration file
  while still defaulting to the templates if a configuration file is not provided.
- Added retry and wait for running ECS tasks when capacity is unavailable.
- Updated performance test documentation.

Tests:

- Remove irrelevant properties from system test configurations.
- Created a JUnit test framework for system tests.
- Converted ingest batcher system test to use new JUnit test framework.

Documentation:

- Added high-level Sleeper design diagram.
- Added details on how to contribute and sign the CLA [here](CONTRIBUTING.md).

Misc:

- Split user defined instance property declarations by property groups.
- Added issue and pull request templates.
- Added VSCode configuration files.
- Update and manage several dependencies to resolve CVEs found by dependency check.
- Added a way to visualise internal dependencies between Maven modules.
- Added a property to force reload the configuration whenever a Lambda is executed.

Bugfixes:

- Fixed an issue where files under directories were not counted correctly in `IngestJobStatusReport`.
- Raised timeout for system tests when waiting for lambdas to run.
- Fixed an issue where the jars bucket failed to tear down because it was not empty during the tearDown process.
- Raised timeout for Lambda starting bulk import jobs
- Stopped Lambda starting bulk import jobs processing multiple jobs at once.
- Fixed an issue where submitting a bulk import job twice with the same ID twice would overwrite the first one.
- Fixed issues where passing one CDK parameter into the environment CLI commands would ignore the parameter.
- Fixed an issue where if a stack failed to delete during the tear down process, it would keep waiting for the
  state to update until it timed out.
- Stopped Docker CLI wrapping terminal commands onto the same line.

## Version 0.17.0

This contains the following improvements:

Ingest batcher:

- Added a new system for batching files into ingest jobs. See [docs/05-ingest.md](./docs/05-ingest.md)
  and [docs/10-design.md](./docs/12-design.md) for more information.

Bulk Import:

- Upgrade EMR version to 6.10.0.
- Upgrade Spark version to 3.3.1.

Development:

- Added devcontainers support.
- Added a script to regenerate properties templates from property definitions.
- Added OWASP Dependency-Check Maven plugin.

Tests:

- Added a way to automatically run system tests and upload the results to an S3 bucket.
- Increase rate at which Fargate tasks are started in system tests.

Misc:

- Upgrade parquet-mr version to 1.13.0.
- Rename `LINES` to `RECORDS` in reports and throughout the project.
- Update properties templates.

Bugfixes:

- Fixed an issue where ingest tasks reported an ingest rate of NaN when exiting immediately.
- Fixed an issue where the default value for a table property did not display when confirming changes in the admin
  client if the property was unset.
- Fixed an issue where tearing down an instance would fail if the config bucket was empty.

## Version 0.16.0

This contains the following improvements:

Trino:

- Added the ability to query Sleeper tables using Trino, see the documentation [here](docs/09-trino.md). This is an
  experimental feature.

Bulk Import:

- Improve observability of bulk import jobs by including them in ingest job status reports.
- Added table property for minimum leaf partition count. If the minimum is not met, bulk import jobs will not be run.

Scripts:

- Added logging output to `DownloadConfig` class.
- Added ability to define splitpoints file in `deploy.sh`.
- Added runnable class to remove log groups left over from old instances (`CleanUpLogGroups`).

CDK:

- Added the flag `deployPaused` to deploy the system in a paused state.
- Add the tag `InstanceId` to all AWS resources when they are deployed.
- Pre-authenticate the environment EC2 instance with AWS.

Clients:

- Added count of input files to compaction job report.
- For persistent EMR bulk import, report on steps that have not started yet in the ingest status report.
- Avoid loading properties unnecessarily in the admin client.
- Refactor compaction and ingest reports to remove unnecessary wrapping of arguments.

Tests:

- Simplify `compactionPerformance` system test to only perform merge compactions.
- Assert output of `compactionPerformance` system test to detect failures
- Create `partitionSplitting` system test, which do not perform merge compactions and only perform splitting
  compactions.
- Create `bulkImportPerformance` system test, which performs a bulk import and does no merge/splitting compactions.
- Reduce code duplication in Arrow ingest test helpers.
- Introduce test fakes for querying properties and status stores in the admin client and reports.

Bugfixes:

- Fixed issue where the queue estimates sometimes did not update before invoking the compaction task lambda in
  the `compactionPerformance` system test.
- Fixed issue where the `tearDown` script failed if non-persistent EMR clusters were still running.
- Fixed issue where `WaitForGenerateData` was excluding 1 task from checks, causing it to not wait if the number of
  tasks was 1.

## Version 0.15.0

This contains the following improvements:

Standard ingest:

- Added ability to define multiple source buckets.

Tables:

- Added ability to export partition information to a file.

Scripts:

- Added a script to add a new table to an existing instance of Sleeper (`scripts/deploy/addTable.sh`).
- Added a script to bring an existing instance of Sleeper up to date (`scripts/deploy/deployExisting.sh`).
- Replace the deployment scripts with Java.

Docker CLI:

- Added a builder docker image, to be used inside EC2 to run scripts and Sleeper CLI commands (`sleeper builder`).
- Added deployment docker image to Sleeper CLI for deploying a pre-built version of Sleeper (`sleeper deployment`).
- Added a command to bring the Sleeper CLI up to date (`sleeper cli upgrade`).
- Added support for Apple M1 and other ARM-based processors.

CDK:

- Added a way to run `cdk deploy` from Java.
- Added a way to run `cdk destroy` from Java.
- Add validation for Sleeper version on `cdk deploy` by default.
- Add the Sleeper CLI to the cdk-environment EC2.
- Add versioning for Lambdas, update when code is changed on `cdk deploy`.

Clients:

- Added a "shopping basket" view to the admin client.
    - Viewing and editing properties now brings you to a text editor where you can make changes and save them.
    - Upon saving and leaving the editor, you will be presented with a summary of your changes, and have the
      option to save these changes to S3, return to the editor, or discard the changes.
    - Any validation issues will appear in the summary screen, and prevent you from saving until they are resolved.
- Properties that require a `cdk deploy` are now flagged, and the `cdk deploy` is performed after changing
  any of these properties.
- Properties are now grouped based on their context.
    - You can also filter properties by group in the admin client.
- Descriptions are now displayed above properties in the editor.
- Properties that cannot be changed (either they are system defined or they require redeploying the instance)
  are included in the validation checks when making changes in the editor.
- Added the following status reports to the admin client main menu:
    - Partitions.
    - Files.
    - Compaction jobs & tasks.
    - Ingest jobs & tasks.

Tests:

- Upgrade LocalStack and DynamoDB in Testcontainers tests.

Bugfixes:

- You can now deploy an EC2 environment into an existing VPC.
- The deploy script no longer fails to find a bucket to upload jars to after creating it.
- All records are now loaded for the compaction and ingest reports - some records were missing when there were
  too many records.
- The compaction performance test can now run with more than 100 data generation ECS tasks.
- Added transitive dependency declarations to built Maven artifacts.
- The partitions status report now displays the correct field name for the split field.
- CDK now references bulk import bucket correctly. Previously you could encounter deployment failures when
  switching bulk import stacks.
- Running the `connectToTable.sh` script no longer clear the generated directory if you encounter an AWS auth
  failure (this has been moved to  `scripts/utility/downloadConfig.sh`).
- The compaction performance test no longer fails if a job started before it was reported as created.
- The compaction performance test no longer fails if the partition splitting job queue size updates too slowly.

## Version 0.14.0

This contains the following improvements:

General code improvements:

- All Java modules have been upgraded to Java 11 and the project can now be built with Java 11 or 17. All
  code executed in lambdas, containers and on EMR now uses Amazon Corretto 11.
- The SleeperProperty enums have been converted into static constants. This will allow additional properties
  to be added to them more easily.
- Upgraded from JUnit 4 to 5.
- Updated versions of many dependencies, including EMR, Spark, Hadoop.

Compactions:

- The lambda that creates more ECS tasks to run compactions previously scaled up too quickly as the calculation
  of the number of tasks currently running ignored pending tasks. This meant that occasionally tasks would start
  up and find they had no work to do.

Standard ingest:

- Fixed bug where the asynchronous file uploader could fail if a file was greater than 5GB.
- Split ingest modules into submodules so that code needed for lambda to start jobs is smaller.

System tests:

- Made system tests more deterministic by explicitly triggering compactions.

Clients:

- Reports now report durations in human-readable format.

Docker CLI:

- Added a CLI for setting up an environment to deploy Sleeper
- Uploaded an image for deploying a fixed, built version of Sleeper to be used in the future

## Version 0.13.0

This contains the following improvements:

General code improvements:

- The various compaction related modules are now all submodules of one parent compaction module.
- Simplified the names of Cloudwatch log groups.

Standard ingest:

- Refactored standard ingest code to simplify it and make it easier to use.
- Observability of ingest jobs: it is now possible to see the status of ingest jobs (i.e. whether they are queued,
  in progress, finished) and how long they took to run.
- Fixed bug where standard ingest would fail if it needed to upload a file greater than 5GB to S3. This was done
  by replacing the use of put object with transfer manager.
- The minimum part size to be used for uploads is now configurable and defaults to 128MB.
- Changed the default value of `sleeper.ingest.arrow.max.local.store.bytes` from 16GB to 2GB to reduce the latency
  before data is uploaded to S3.
- Various integration tests were converted to unit tests to speed up the build process.

Bulk import:

- Added new Dataframe based approach to bulk import that uses a custom partitioner so that Spark partitions the
  data according to Sleeper's leaf partitions. The data is then sorted within those partitions. This avoids the
  global sort required by the other Dataframe based approach, and means there is one fewer pass through the data
  to be loaded. This reduced the time of a test bulk import job from 24 to 14 minutes.
- EBS storage can be configured for EMR clusters created for bulk import jobs.
- Bumped default EMR version to 6.8.0 and Spark version to 3.3.0.

Compactions:

- Compactions can now be run on Graviton Fargate containers.

Scripts:

- The script to report information about the partitions now reports more detailed information about the number of
  elements in a partition and whether it needs splitting.
- System test script reports elapsed time.

Build:

- Various improvments to github actions reliability.
- Created a Docker image that can be used to deploy Sleeper. This avoids the user needing to install multiple tools
  locally.

## Version 0.12.0

This contains the following improvements:

General code improvements:

- Added spotbugs.
- Added checkstyle.
- GitHub actions are now used for CI.
- Builders added to the Java BulkImportJob, CompactionJob, FileInfo, Partition, Query and Schema classes.
- Use AssertJ for test assertions, and enforce that this is used instead of Junit assertions.

Core:

- Improved testing of RangeCanonicaliser.
- Schema now checks that field names are unique.
- Top level pom now specifies source encoding.
- A UUID is now always used for the name of a partition.

Queries:

- SqsQueryProcessorLambda now allows the number of threads in the executor service to be configured.

Compactions:

- Recording of the status of compaction jobs in DynamoDB, with scripts to enable querying of the status of a job or all
  jobs.
- Recording of the status of compaction ECS tasks in DynamoDB, with scripts to enable querying of the status of a task
  or all tasks.
- Various integration tests converted to unit tests to speed up build process.
- Fixed error in `DeleteMessageAction` log message.

Bulk imports:

- Persistent EMR bulk import stack now uses a lambda to submit an EMR step to the cluster to run the bulk import job.
  The job is written
  to a file in S3 in the bulk import logs bucket.
- The default class to use for bulk imports can now be configured as an instance property.
- `spark.sql.shuffle.partitions` option is now set on Spark bulk import jobs.
- The Spark properties used across the EMR and persistent EMR stacks are now consistent.
- EMR clusters used in the (non-persistent) EMR stack now have the security group provided by the user added to allow
  ssh into the master.
- Bulk import stacks now always create a logs bucket.

State store:

- DynamoDB and S3 state stores refactored into smaller classes.

Ingest:

- Default value of `sleeper.ingest.arrow.working.buffer.bytes` has been increased to 256MB.
- Improved logging in `ArrowRecordBatchBase`.
- The documentation for the `sleeper.ingest.arrow.max.single.write.to.file.bytes` parameter in the example instance
  properties
  file was incorrectly described.
- The Arrow-based record batching approach now supports schemas with maps and lists.
- The `IngestCoordinator` class no longer makes an unnecessary request to list the partitions.

CDK:

- Added CDK stack to create an EC2 instance that can be used to deploy Sleeper from.
- Added optional instance property that allows a tag name to be provided that will be used to tag the different stacks
  with the tag name.
- The topic stack now checks that the email address provided is not the empty string.

Scripts:

- Added a script to regenerate the `generated` directory used in scripts.
- Files of split points added to system-test scripts directory to allow testing with different numbers of partitions.
- Fixed incorrect message in scripts that upload Docker images.
- Files status report script prints out number of records in a human readable form.
- Fixed issue with use of `declare -A` in scripts that only worked on bash version 4 and later.
- Fixed issue with use of `sed -i` that causes deploy scripts to fail on Macs.

Python API:

- Bulk import class name can now be configured when submitting a bulk import job from Python.
- Fixed bug in `create_batch_writer` method.

Documentation:

- Added note about CloudWatch metrics stack.
- Fixed various minor formatting issues.
- Improved documentation of bulk import process.
- Clarified required versions of command-line dependencies such as Maven, Java, etc.
- Corrected error in bulk import key pair property name in docs and `example/full/instance.properties`.
- Clarified documentation on `cdk bootstrap` process.

## Version 0.11.0

First public release.
