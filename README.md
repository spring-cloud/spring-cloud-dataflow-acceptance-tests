# Spring Cloud Data Flow is no longer maintained as an open-source project by Broadcom, Inc.

## For information about extended support or enterprise options for Spring Cloud Data Flow, please read the official blog post [here](https://spring.io/blog/2025/04/21/spring-cloud-data-flow-commercial).

# Spring Cloud Data Flow Acceptance Tests

## Build Status

| Environment | Rabbit MQ | Kafka |
|---|----------|--------|
| 0 | ![rabbit-env-0](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-rabbit-0.yml/badge.svg) | ![kafka-env-0](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-kafka-0.yml/badge.svg) |
| 1 | ![rabbit-env-1](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-rabbit-1.yml/badge.svg) | ![kafka-env-1](https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/actions/workflows/acceptance-tests-for-kafka-1.yml/badge.svg) |

## About
This project installs Spring Cloud Dataflow to cloudfoundry environments and executes a series of integration tests deploying streams and tasks in different scenarios.

## NOTE:
Currently, this repo is used only to test Cloud Foundry environments, K8s acceptance tests use GH Actions in the [spring-cloud-dataflow](https://github.com/spring-cloud/spring-cloud-dataflow) repository.
The java test code here is the same used by the K8s build.


## How to run the tests

The main script is `scripts/at-test-script.sh`.
It requires configuration via environment variables.
See [scdf_cf_setup](https://github.com/dturanski/scdf_cf_setup#readme) for details.

```
USAGE: ./scripts/at-test-script.sh
```

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

### Contributions
All commits must include a **Signed-off-by** trailer at the end of each commit message to indicate that the contributor agrees to the Developer Certificate of Origin.
For additional details, please refer to the blog post https://spring.io/blog/2025/01/06/hello-dco-goodbye-cla-simplifying-contributions-to-spring[Hello DCO, Goodbye CLA: Simplifying Contributions to Spring].
