Deploying to LocalStack in Docker
=================================

You can run a LocalStack container locally and deploy an instance of Sleeper to it. This deployment method has limited
functionality and will only work with small volumes of data, but will allow you to perform a queue-based standard
ingest, and run reports and scripts against the instance.

## Prerequesites

The easiest way to run the LocalStack deployment commands is to use the Sleeper CLI, along with the deployment docker
image. You can get to a command line inside the deployment image by running the following command:

```shell
sleeper deployment
```

This will put you in the `scripts` directory with all of the tools required to run Sleeper on LocalStack installed.
All commands that follow will assume you are working inside the sleeper-deployment docker container.

## Launch LocalStack container

To launch the LocalStack container, you can run the following command:

```shell
./deploy/localstack/startContainer.sh
```

This will also output commands you can use to point Sleeper scripts to your LocalStack container.

## Deploy to LocalStack

For Sleeper commands to interact with LocalStack, ensure that the `AWS_ENDPOINT_URL` environment variable
is set. Commands to do this are provided by the `startContainer.sh` script, but you can also manually set this by
running the following commands:

- If you are inside a docker container:
```shell
export AWS_ENDPOINT_URL=http://host.docker.internal:4566
```

- If you are on your host machine:
```shell
export AWS_ENDPOINT_URL=http://localhost:4566
```

To go back to using the default AWS endpoint, you can unset this environment variable:

```shell
unset AWS_ENDPOINT_URL
```

To deploy an instance of Sleeper to your LocalStack container, you can run the following command:

```shell
./deploy/localstack/deploy.sh <instance-id>
```

Note that you will not be able to run this command unless you have the AWS_ENDPOINT_URL environment variable
set as described in the previous section.

This will create a config bucket and a table bucket in LocalStack, and upload the necessary properties files.
A single table will be created with the name `system-test`.

Once the instance is deployed, you can launch the admin client to view the instance and table properties of the
instance, as well as running partition and file status reports.

```shell
./utility/adminClient.sh <instance-id>
```

## Standard ingest

You can generate some random data for your instance by running the following command:

```shell
./deploy/localstack/generateRandomData.sh <instance-id> <table-name> <optional-number-of-records>
```

This will place randomly generated parquet files in the `./deploy/localstack/output` directory. The number of files
generated will depend on the number of records that you pass into the script. By default only 1 file is generated.

You can then use these files to ingest some data into the `system-test` table in your instance by running the
following command:

```shell
./deploy/localstack/ingestFiles.sh <instance-id> <table-name> <file1.parquet> <file2.parquet> <file3.parquet> ....
```

This script will upload the provided files to an ingest source bucket in LocalStack, create ingest jobs, and
send them to the ingest job queue. It will then build the ingest-runner docker image, and launch a container for it,
which will take the ingest job off the queue and perform the ingest.

You can then view the ingest jobs and task that were run by launching the admin client and running an ingest job or
ingest task status report.

You can skip the step of having to run `ingestFiles.sh` after generating some parquet files by running the
following command:

```shell
./deploy/localstack/ingestRandomData.sh <instance-id> <table-name> <optional-number-of-records>
```

Note: If you do not provide a number of records in the data generation scripts, then a default of 100000 is used.

## Query data

To query the data in your Sleeper instance, you can run the following utility script. Note that lambda queries and
web socket queries do not work against a Sleeper instance deployed against LocalStack.

```shell
./utility/query.sh <instance-id>
```

## Tear down instance

You can tear down an existing instance by running the following command:

```shell
./deploy/localstack/tearDown.sh <instance-id>
```

## Stop LocalStack container

To stop the LocalStack container, you can run the following command:

```shell
./deploy/localstack/stopContainer.sh
```