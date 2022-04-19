# Spring Cloud Data Flow Acceptance Tests

## Build Status

| Environment | Rabbit MQ | Kafka |
|---|----------|--------|
| 0 | ![rabbit-env-0](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-rabbit-0.yml/badge.svg) | ![kafka-env-0](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-kafka-0.yml/badge.svg) |
| 1 | ![rabbit-env-1](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-rabbit-1.yml/badge.svg) | ![kafka-env-1](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-kafka-1.yml/badge.svg) |
| 2 | ![rabbit-env-2](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-rabbit-2.yml/badge.svg) | ![kafka-env-2](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-kafka-2.yml/badge.svg)|

## About
This project bootstraps a dataflow server on a target platform (certified local, kubernetes, cloudfoundry environments), executes a series of tests by creating a series of streams and tasks and then cleans up after its done.

## NOTE:
Currently, these shell scripts are used only to test Cloud Foundry environments, K8s acceptance tests use GH Actions in the [spring-cloud-dataflow](https://github.com/spring-cloud/spring-cloud-dataflow) repository. The java test code here is used by the K8s build as will.




## How to run the tests

The main script is called `run.sh` and is a wrapper to run tasks for three phases:

* Setup: The [setup](scripts/setup.adoc) phase will traverse each folder and call `create.sh` scripts.
At the end of this phase you should expect to have an environment available with the Spring Cloud Data Flow server with a verified `SERVER_URI` and `SKIPPER_SERVER_URI`, along with the services required for it to run.
* Test: [test](scripts/tests.adoc) phase will invoke the `mvn test`. The Junit test fixtures deploy test apps, using the `DataFlowTemplate`, into the configured environment and verify that the streams and tasks complete as expected.
* Clean: [clean](scripts/clean.adoc) phase will undeploy the server and remove any services.

### NOTE:
For convenience, `test` includes `clean` after by default. You can set `--skipClean` to disable this.

```
USAGE: run.sh <phase> <phase-args>
```

The first option is to choose a *PLATFORM*, available options `cloudfoundry`, `kubernetes`, and `local`.
The scripts specific to each platform are located in directories named for the corresponding platform.

## Examples

### To run the tests on cloudfoundry:

```
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL=https://api...
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG=<org>>
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE=<space>
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN=apps....
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME=<username,email>
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD=<password>
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION=true
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_STREAM_SERVICES=rabbit
#If schedules enabled
export SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_TASK_SERVICES=mysql,ci-scheduler
export MYSQL_SERVICE_NAME=<mysql service name>
export MYSQL_PLAN_NAME=<service plan name
export RABBIT_SERVICE_NAME=<rabbit service name>
export RABBIT_PLAN_NAME=<rabbit plan name>
#export DEPLOY_PAUSE_TIME=10
#export TRUST_CERTS=<trust cert domain>
#export CF_DIAL_TIMEOUT=600
export JAVA_BUILDPACK=java_buildpack_offline
export SCHEDULES_URL=https://scheduler.apps...

run.sh clean -p cloudfoundry
run.sh setup -p cloudfoundry -b rabbit -se -dv 2.5.0.BUILD-SNAPSHOT -sv 2.3.0.BUILD-SNAPSHOT -cc
run.sh tests -p cloudfoundry -b rabbit
```

### To run the tests on kubernetes:

```
#The kubernetes config location, e.g., ~/.kube/config
export KUBECONFIG=config location
export CLUSTER_NAME=<cluster-name>
#By default the setup will perform a helm install. Uncomment to install directly from the git repo.
#export USE_DISTRO_FILES=true
#From SCDF 2.6 and above, the bitnami chart should be used. For below, the legacy Helm stable repo can be used:
#export USE_LEGACY_HELM_CHART=true

run.sh clean -p kubernetes -se #schedules enabled
run.sh setup -p kubernetes -b rabbit -se -dv 2.5.0.BUILD-SNAPSHOT -sv 2.3.0.BUILD-SNAPSHOT
run.sh tests -p kubernetes -b rabbit -se
```

###NOTE:
The commands require settings to be repeated, to avoid this, you can use environment variables for common options:

```
export KUBECONFIG=config location
export CLUSTER_NAME=<cluster-name>
export PLATFORM=kubernetes
export BINDER=rabbit
export DATAFLOW_VERSION=2.5.0.BUILD-SNAPSHOT
export SKIPPER_VERSION=2.3.0.BUILD-SNAPSHOT

run.sh clean -se
run.sh setup -se
run.sh tests -se
```

= General configuration

Make sure you have `JAVA_HOME` configured correctly in your environment.

Each platform has different flags, but the global ones should be:

* RETRIES : Number of times to test for a port when checking a service status (6 by default)
* WAIT_TIME: How long to wait for another port test (5s by default)
* SPRING_CLOUD_DATAFLOW_SERVER_DOWNLOAD_URL: Location of the dataflow jar file to be downloaded (optional).
* DATAFLOW_VERSION: The Dataflow version
* SKIPPER_VERSION: The Skipper version
* PLATFORM: the platform type (local, kubernetes, cloudfoundry)
* BINDER: the binder (rabbit, kafka)
* DEBUG: Log debug output
* USE_HTTPS: Use https to post data to test streams

To point to where your server is located and also specify which artifacts you want to register with the server.

# Running Scheduling Acceptance Tests

By default, scheduling acceptance tests are disabled, because not all data flow implementations support it yet.
To enable scheduling acceptance tests use the `-se` flag.
Also you will need to set the scheduler URL if the platform is `cloudfoundry`.  For example `export SCHEDULES_URL#<your scheduler url>`.

###NOTE:
Currently Spring Cloud Data Flow Cloud Foundry and Kubernetes are the only implementations that support scheduling.

# Platform specific notes

## Local

### Pre-requisites

* `docker` and `docker-compose` installed.  Make sure you can connect to the docker daemon without using 'sudo', e.g. `docker info` works.

* `$DOCKER_SERVER` environment variable properly set.  Defaults to localhost, which works on unix.  For MacOS `192.168.99.100` should work.

If a local service is not found, the script will try to deploy using `docker-compose` so it's important that
you have that installed and configured properly.

When cleaning up, the script will only remove docker images, if you are using a local service like mysql
the script will not do anything to it

## CloudFoundry

### Pre-requisites
On Cloudfoundry, make sure you have the following environment variables exported. We will not include them on any files
to prevent it to be leaked into github repos with credentials.

* SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL
* SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN
* SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME
* SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD
* SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION
* SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_STREAM_SERVICES=rabbit

### Configuration

Currently, Cloud Foundry testing requires an external SQL Database.
The following environment variables are required:

* SQL_USERNAME
* SQL_PASSWORD
* SQL_HOST
* SQL_PORT

The creation and deletion of services are implemented as blocking functions, i.e. a test job will wait, for instance,
during setup until a service is created before continuing.
After requesting CloudFoundry to create or delete a service, these functions periodically poll until the request has been fully met.
The defaults for the  number of polls and the delay between polling can be overridden using the following properties:

* SCDFAT_RETRY_MAX _(default 100, set to <0 for no max)_
* SCDFAT_RETRY_SLEEP _(in seconds, default 5)_

## Kubernetes (vSphere)

### Pre-requisites

* The `kubectl` command line tool needs to be installed. Installation information can be found [here](https://kubernetes.io/docs/tasks/tools/install-kubectl).

### Configuration

The following environment variables must be set:

* KUBECONFIG - the path to the kube config file to use
* CLUSTER_NAME - the name of the cluster to target (must be present in KUBECONFIG)

Optional settings:

* KUBERNETES_NAMESPACE environment variable that specifies an existing namespace to use for the testing. If this is not specified, the 'default' namespace will be used.
* DATAFLOW_SERVICE_ACCOUNT_NAME the service account name to create and configure for server access (defaults to `scdf-sa`)

### Code formatting guidelines

* The directory `/etc/eclipse` has two files for use with code formatting, `eclipse-code-formatter.xml` for the majority of the code formatting rules and `eclipse.importorder` to order the import statements.

* In eclipse you import these files by navigating `Windows -> Preferences` and then the menu items `Preferences > Java > Code Style > Formatter` and `Preferences > Java > Code Style > Organize Imports` respectfully.

* In `IntelliJ`, install the plugin `Eclipse Code Formatter`.
You can find it by searching the "Browse Repositories" under the plugin option within `IntelliJ` (Once installed you will need to reboot Intellij for it to take effect).
Then navigate to `Intellij IDEA > Preferences` and select the Eclipse Code Formatter.
Select the `eclipse-code-formatter.xml` file for the field `Eclipse Java Formatter config file` and the file `eclipse.importorder` for the field `Import order`.
Enable the `Eclipse code formatter` by clicking `Use the Eclipse code formatter` then click the *OK* button.

### NOTE:
If you configure the `Eclipse Code Formatter` from `File > Other Settings > Default Settings` it will set this policy across all of your Intellij projects.
