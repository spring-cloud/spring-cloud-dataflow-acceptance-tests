/*
 * Copyright 2021-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.acceptance.test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.JsonPath;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.assertj.core.api.Condition;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.batch.core.BatchStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.integration.test.tags.DockerCompose;
import org.springframework.cloud.dataflow.integration.test.util.AwaitUtils;
import org.springframework.cloud.dataflow.integration.test.util.DockerComposeFactory;
import org.springframework.cloud.dataflow.integration.test.util.RuntimeApplicationHelper;
import org.springframework.cloud.dataflow.rest.client.AppRegistryOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowClientException;
import org.springframework.cloud.dataflow.rest.client.dsl.DeploymentPropertiesBuilder;
import org.springframework.cloud.dataflow.rest.client.dsl.Stream;
import org.springframework.cloud.dataflow.rest.client.dsl.StreamApplication;
import org.springframework.cloud.dataflow.rest.client.dsl.StreamDefinition;
import org.springframework.cloud.dataflow.rest.client.dsl.task.Task;
import org.springframework.cloud.dataflow.rest.client.dsl.task.TaskBuilder;
import org.springframework.cloud.dataflow.rest.resource.DetailedAppRegistrationResource;
import org.springframework.cloud.dataflow.rest.resource.JobExecutionResource;
import org.springframework.cloud.dataflow.rest.resource.StreamDeploymentResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionStatus;
import org.springframework.cloud.dataflow.rest.resource.about.AboutResource;
import org.springframework.cloud.skipper.domain.SpringCloudDeployerApplicationManifestReader;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpMethod;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Christian Tzolov
 * @author Corneil du Plessis
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(DataFlowOperationsATConfiguration.class)
@DockerCompose
class DataFlowAT extends CommonTestBase {

    /**
     * Folder that collects the external docker-compose YAML files such as coming from
     * external classpath, http/https or file locations. Note: Needs to be static, because as
     * a part of the dockerCompose extension it is shared with all tests. TODO: Explore if the
     * temp-folder can be created and destroyed internally inside the dockerCompose extension.
     */
    @TempDir
    static Path tempDockerComposeYamlFolder;

    /**
     * A JUnit 5 extension to bring up Docker containers defined in docker-compose-xxx.yml
     * files before running tests. You can set either test.docker.compose.disable.extension
     * property of DISABLE_DOCKER_COMPOSE_EXTENSION variable to disable the extension.
     */
    @RegisterExtension
    public static Extension dockerCompose = DockerComposeFactory.startDockerCompose(tempDockerComposeYamlFolder);

    @BeforeEach
    public void before() {
        logger.debug("before:start");
        logger.info("[platform = {}, type = {}]", runtimeApps.getPlatformName(), runtimeApps.getPlatformType());
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(5));
        Awaitility.setDefaultTimeout(Duration.ofMinutes(10));
        registerTimestampTasks();
        resetTimestampVersion();
        logger.debug("before:end");
    }


    @AfterEach
    public void after() {
        logger.debug("after:start");
        try {
            dataFlowOperations.streamOperations().destroyAll();
            logger.info("Destroyed all streams");
        } catch (Exception e) {
            logger.error("after:" + e.getMessage());
        } finally {
            try {
                dataFlowOperations.taskOperations().list().forEach(taskDefinitionResource -> {
                    logger.info("Destroying task {} and execution history", taskDefinitionResource.getName());
                    dataFlowOperations.taskOperations().destroy(taskDefinitionResource.getName(), true);
                });
                logger.info("Destroyed all tasks and execution history");
            } catch (Exception e) {
                logger.error("after:" + e.getMessage());
            }
        }
        logger.debug("after:end");
    }

    @Test
    @Order(Integer.MIN_VALUE)
    public void aboutTestInfo() {
        logger.info("Available platforms: " + dataFlowOperations.streamOperations().listPlatforms().stream()
            .map(d -> String.format("[name: %s, type: %s]", d.getName(), d.getType()))
            .collect(Collectors.joining()));
        logger.info(String.format("Selected platform: [name: %s, type: %s]", runtimeApps.getPlatformName(),
            runtimeApps.getPlatformType()));
        logger.info("Wait until at least 60 apps are registered in SCDF");
        Awaitility.await()
            .until(() -> dataFlowOperations.appRegistryOperations().list().getMetadata().getTotalElements() >= 60L);
    }

    @Test
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "kubernetes")
    public void applicationMetadataMavenTests() {
        logger.info("application-metadata-maven-test");

        // Maven app with metadata
        DetailedAppRegistrationResource mavenAppWithJarMetadata = dataFlowOperations.appRegistryOperations()
            .info("file", ApplicationType.sink, false);
        assertThat(mavenAppWithJarMetadata.getOptions()).describedAs("mavenAppWithJarMetadata").hasSize(8);

        // Maven app without metadata
        dataFlowOperations.appRegistryOperations().register("maven-app-without-metadata", ApplicationType.sink,
            "maven://org.springframework.cloud.stream.app:file-sink-kafka:3.0.1", null, true);
        DetailedAppRegistrationResource mavenAppWithoutMetadata = dataFlowOperations.appRegistryOperations()
            .info("maven-app-without-metadata", ApplicationType.sink, false);
        assertThat(mavenAppWithoutMetadata.getOptions()).describedAs("mavenAppWithoutMetadata").hasSize(8);
        // unregister the test apps
        dataFlowOperations.appRegistryOperations().unregister("maven-app-without-metadata", ApplicationType.sink);
    }

    @Test
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry")
    public void applicationMetadataDockerTests() {
        logger.info("application-metadata-docker-test");

        // Docker app with container image metadata
        dataFlowOperations.appRegistryOperations().register("docker-app-with-container-metadata",
            ApplicationType.source,
            "docker:springcloudstream/time-source-kafka:2.1.4.RELEASE", null, true);
        DetailedAppRegistrationResource dockerAppWithContainerMetadata = dataFlowOperations.appRegistryOperations()
            .info("docker-app-with-container-metadata", ApplicationType.source, false);
        assertThat(dockerAppWithContainerMetadata.getOptions()).hasSize(6);

        // Docker app with container image metadata with escape characters.
        dataFlowOperations.appRegistryOperations().register("docker-app-with-container-metadata-escape-chars",
            ApplicationType.source,
            "docker:springcloudstream/http-source-rabbit:2.1.3.RELEASE", null, true);
        DetailedAppRegistrationResource dockerAppWithContainerMetadataWithEscapeChars = dataFlowOperations
            .appRegistryOperations()
            .info("docker-app-with-container-metadata-escape-chars", ApplicationType.source, false);
        assertThat(dockerAppWithContainerMetadataWithEscapeChars.getOptions()).hasSize(6);

        // Docker app without metadata
        dataFlowOperations.appRegistryOperations().register("docker-app-without-metadata", ApplicationType.sink,
            "docker:springcloudstream/file-sink-kafka:2.1.1.RELEASE", null, true);
        DetailedAppRegistrationResource dockerAppWithoutMetadata = dataFlowOperations.appRegistryOperations()
            .info("docker-app-without-metadata", ApplicationType.sink, false);
        assertThat(dockerAppWithoutMetadata.getOptions()).hasSize(0);

        // Docker app with jar metadata
        dataFlowOperations.appRegistryOperations().register("docker-app-with-jar-metadata", ApplicationType.sink,
            "docker:springcloudstream/file-sink-kafka:2.1.1.RELEASE",
            "maven://org.springframework.cloud.stream.app:file-sink-kafka:jar:metadata:2.1.1.RELEASE", true);
        DetailedAppRegistrationResource dockerAppWithJarMetadata = dataFlowOperations.appRegistryOperations()
            .info("docker-app-with-jar-metadata", ApplicationType.sink, false);
        assertThat(dockerAppWithJarMetadata.getOptions()).hasSize(8);

        // unregister the test apps
        dataFlowOperations.appRegistryOperations().unregister("docker-app-with-container-metadata",
            ApplicationType.source);
        dataFlowOperations.appRegistryOperations().unregister("docker-app-with-container-metadata-escape-chars",
            ApplicationType.source);
        dataFlowOperations.appRegistryOperations().unregister("docker-app-without-metadata", ApplicationType.sink);
        dataFlowOperations.appRegistryOperations().unregister("docker-app-with-jar-metadata", ApplicationType.sink);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BINDER", matches = "rabbit")
    public void multipleStreamApps() {
        if (this.runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            registerApp("kitchen", "docker:springcloudstream/scdf-app-kitchen:1.0.0-SNAPSHOT");
            registerApp("customer", "docker:springcloudstream/scdf-app-customer:1.0.0-SNAPSHOT");
            registerApp("waitron", "docker:springcloudstream/scdf-app-waitron:1.0.0-SNAPSHOT");
        } else {
            registerApp("kitchen", "maven:io.spring:scdf-app-kitchen:1.0.0-SNAPSHOT");
            registerApp("customer", "maven:io.spring:scdf-app-customer:1.0.0-SNAPSHOT");
            registerApp("waitron", "maven:io.spring:scdf-app-waitron:1.0.0-SNAPSHOT");
        }
        try {
            logger.info("multipleStreamApps:check:restaurant-test");
            StreamDeploymentResource streamResource = dataFlowOperations.streamOperations().info("restaurant-test");
            if (streamResource.getStatus().equals("DEPLOYED")) {
                logger.info("multipleStreamApps:undeploy:restaurant-test");
                dataFlowOperations.streamOperations().undeploy("restaurant-test");
            }
            logger.info("multipleStreamApps:destroy:restaurant-test");
            dataFlowOperations.streamOperations().destroy("restaurant-test");
        } catch (DataFlowClientException x) {
            logger.debug("Checking:restaurant-test:" + x);
        }
        logger.info("multipleStreamApps:define:restaurant-test");
        StreamDefinition streamDefinition = Stream.builder(dataFlowOperations)
            .name("restaurant-test")
            .definition("kitchen || waitron || customer")
            .create();
        logger.info("multipleStreamApps:deploy:restaurant-test");
        StreamApplication waitron = new StreamApplication("waitron");
        Stream destroy = null;
        try {
            final Stream stream = streamDefinition.deploy(
                new DeploymentPropertiesBuilder()
                    .putAll(testDeploymentProperties("waitron"))
                    // Input from Waitron
                    .put("app.kitchen.spring.cloud.stream.bindings.orders.destination", "ordersDest")
                    .put("app.kitchen.spring.cloud.stream.bindings.staff.destination", "staffDest")
                    // Output to Waitron
                    .put("app.kitchen.spring.cloud.stream.bindings.food.destination", "foodDest")
                    .put("app.kitchen.spring.cloud.stream.bindings.hotDrinks.destination", "hotDrinksDest")
                    .put("app.kitchen.spring.cloud.stream.bindings.coldDrinks.destination", "coldDrinksDest")
                    // Output to Customers
                    .put("app.kitchen.spring.cloud.stream.bindings.open.destination", "openDest")
                    // Input from Kitchen
                    .put("app.waitron.spring.cloud.stream.bindings.food.destination", "foodDest")
                    .put("app.waitron.spring.cloud.stream.bindings.hotDrinks.destination", "hotDrinksDest")
                    .put("app.waitron.spring.cloud.stream.bindings.coldDrinks.destination", "coldDrinksDest")
                    // Output to Kitchen
                    .put("app.waitron.spring.cloud.stream.bindings.orders.destination", "ordersDest")
                    .put("app.waitron.spring.cloud.stream.bindings.at_work.destination", "staffDest")
                    // Input from Customer
                    .put("app.waitron.spring.cloud.stream.bindings.order.destination", "orderDest")
                    .put("app.waitron.spring.cloud.stream.bindings.payment.destination", "paymentDest")
                    // Output to Customer
                    .put("app.waitron.spring.cloud.stream.bindings.delivery.destination", "receiveDest")
                    // Input from Restaurant
                    .put("app.customer.spring.cloud.stream.bindings.open.destination", "openDest")
                    // Output to Waitron
                    .put("app.customer.spring.cloud.stream.bindings.order.destination", "orderDest")
                    .put("app.customer.spring.cloud.stream.bindings.payment.destination", "paymentDest")
                    // Input from Waitron
                    .put("app.customer.spring.cloud.stream.bindings.receive.destination", "receiveDest")
                    .build()
            );
            logger.info("multipleStreamApps:waiting-for-deployment:restaurant-test");
            destroy = stream;
            final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            Awaitility.await()
                // .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.getStatus().equals(DEPLOYED));

            logger.info("multipleStreamApps:waiting for acceptPayment:restaurant-test");
            final AwaitUtils.StreamLog waitronLog = AwaitUtils.logOffset(stream, "waitron");
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> AwaitUtils.hasInLog(waitronLog, "acceptPayment"));
        } catch (AssertionError x) {
            throw x;
        } catch (Throwable x) {
            logger.error("multipleStreamApps:exception:" + x, x);
            fail(x);
        } finally {
            if (destroy != null) {
                destroy.destroy();
            }
        }
        logger.info("multipleStreamApps:done:restaurant-test");
    }

    @Test
    public void timestampTask() {
        logger.info("task-timestamp-test:start");
        assertTaskRegistration("testtimestamp");
        try (Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("testtimestamp")
            .description("Test timestamp task")
            .build()) {

            // task first launch
            long launchId1 = task.launch();

            validateSuccessfulTaskLaunch(task, launchId1);

            // task second launch
            long launchId2 = task.launch();

            Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.execution(launchId2).isPresent()).isTrue();
            assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            // All
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));
        }
        logger.info("task-timestamp-test:done");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SCDF_CR_TEST", matches = "true")
    public void githubContainerRegistryTests() {
        containerRegistryTests("github-log-sink",
            "docker:ghcr.io/tzolov/log-sink-rabbit:3.1.0-SNAPSHOT");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SCDF_CR_TEST", matches = "true")
    public void azureContainerRegistryTests() {
        containerRegistryTests("azure-log-sink",
            "docker:scdftest.azurecr.io/springcloudstream/log-sink-rabbit:3.1.0-SNAPSHOT");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SCDF_CR_TEST", matches = "true")
    public void harborContainerRegistryTests() {
        containerRegistryTests("harbor-log-sink",
            "docker:projects.registry.vmware.com/scdf/scdftest/log-sink-rabbit:3.1.0-SNAPSHOT");
    }

    private void containerRegistryTests(String appName, String appUrl) {
        logger.info("application-metadata-{}-container-registry-test:start", appName);

        // Docker app with container image metadata
        dataFlowOperations.appRegistryOperations().register(appName, ApplicationType.sink,
            appUrl, null, true);
        DetailedAppRegistrationResource dockerAppWithContainerMetadata = dataFlowOperations.appRegistryOperations()
            .info(appName, ApplicationType.sink, false);
        assertThat(dockerAppWithContainerMetadata.getOptions()).hasSize(3);

        // unregister the test apps
        dataFlowOperations.appRegistryOperations().unregister(appName, ApplicationType.sink);
        logger.info("application-metadata-{}-container-registry-test:done", appName);
    }

    // -----------------------------------------------------------------------
    // PLATFORM TESTS
    // -----------------------------------------------------------------------
    @Test
    public void featureInfo() {
        logger.info("platform-feature-info-test:start");
        AboutResource about = dataFlowOperations.aboutOperation().get();
        assertThat(about.getFeatureInfo().isAnalyticsEnabled()).isTrue();
        assertThat(about.getFeatureInfo().isStreamsEnabled()).isTrue();
        assertThat(about.getFeatureInfo().isTasksEnabled()).isTrue();
        logger.info("platform-feature-info-test:end");
    }

    @Test
    public void appsCount() {
        logger.info("platform-apps-count-test:start");
        assertThat(dataFlowOperations.appRegistryOperations().list().getMetadata().getTotalElements())
            .isGreaterThanOrEqualTo(60L);
        logger.info("platform-apps-count-test:end");
    }

    // -----------------------------------------------------------------------
    // STREAM TESTS
    // -----------------------------------------------------------------------

    /**
     * Target Data FLow platform to use for the testing:
     * https://dataflow.spring.io/docs/concepts/architecture/#platforms
     * <p>
     * By default the Local (e.g. platformName=default) Data Flow environment is used for
     * testing. If you have provisioned docker-compose file to add remote access ot CF or K8s
     * environments you can use the target platform/account name instead.
     */
    private static final String SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME = "spring.cloud.dataflow.skipper.platformName";

    // Stream lifecycle states
    public static final String DEPLOYED = "deployed";

    public static final String DELETED = "deleted";

    public static final String UNDEPLOYED = "undeployed";

    public static final String DEPLOYING = "deploying";

    public static final String PARTIAL = "partial";

    public static final Set<String> starting = new HashSet<>(Arrays.asList(DEPLOYING, PARTIAL, DEPLOYED));

    @Test
    public void streamTransform() {
        logger.info("stream-transform-test:start");
        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("transform-test")
            .definition("http | transform --expression=payload.toUpperCase() | log")
            .create()
            .deploy(testDeploymentProperties("http"))
        ) {
            final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            logger.info("stream-transform-test:deploying:{}", stream.getName());
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            logger.info("stream-transform-test:deployed:{}", stream.getName());
            String message = "Unique Test message: " + new Random().nextInt();

            runtimeApps.httpPost(stream.getName(), "http", message);
            logger.info("stream-transform-test:sent:{}:{}", stream.getName(), message);
            final AwaitUtils.StreamLog logOffset = AwaitUtils.logOffset(stream, "log");
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> AwaitUtils.hasInLog(logOffset, message.toUpperCase()));
        }
        logger.info("stream-transform-test:done");
    }


    @Test
    public void streamPartitioning() {
        logger.info("stream-partitioning-test:start (aka. WoodChuckTests)");
        StreamDefinition streamDefinition = Stream.builder(dataFlowOperations)
            .name("partitioning-test")
            .definition("http | splitter --expression=payload.split(' ') | log")
            .create();

        try (Stream stream = streamDefinition.deploy(new DeploymentPropertiesBuilder()
            .putAll(testDeploymentProperties("http", "log"))
            .put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
            // Create 2 log instances with partition key computed from the payload.
            .put("deployer.log.count", "2")
            .put("app.splitter.producer.partitionKeyExpression", "payload")
            .put("app.log.spring.cloud.stream.kafka.bindings.input.consumer.autoRebalanceEnabled", "false")
            .put("app.log.logging.pattern.level",
                "WOODCHUCK-${INSTANCE_INDEX:${CF_INSTANCE_INDEX:${spring.cloud.stream.instanceIndex:666}}} %5p")
            .build())) {
            logger.info("streamPartitioning:deploying:{}", stream.getName());
            final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            logger.info("streamPartitioning:deployed:{}", stream.getName());
            String message = "How much wood would a woodchuck chuck if a woodchuck could chuck wood";
            logger.info("streamPartitioning:sending:{}:{}", stream.getName(), message);
            runtimeApps.httpPost(stream.getName(), "http", message);
            logger.info("streamPartitioning:sent:{}:{}", stream.getName(), message);
            final List<String> woodChuck0 = asList("WOODCHUCK-0", "How", "chuck");
            final List<String> woodChuck1 = asList("WOODCHUCK-1", "much", "wood", "would", "if", "a", "woodchuck", "could");
            Awaitility.await()
                // .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> {
                    Collection<String> logs = runtimeApps.applicationInstanceLogs(stream.getName(), "log").values();
                    logger.info("streamPartitioning:logs:{}", logs);
                    return (logs.size() == 2) && logs.stream()
                        // partition order is undetermined
                        .map(log -> (log.contains("WOODCHUCK-0"))
                            ? woodChuck0.stream().allMatch(log::contains)
                            : woodChuck1.stream().allMatch(log::contains)
                        )
                        .reduce(Boolean::logicalAnd)
                        .orElse(false);
                });
        }
        logger.info("stream-partitioning-test:done (aka. WoodChuckTests)");
    }

    @Test
    @Order(Integer.MIN_VALUE + 10)
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry")
    public void streamAppCrossVersion() {

        final String VERSION_2_1_5 = "2.1.5.RELEASE";
        final String VERSION_3_0_1 = "3.0.1";

        Assumptions.assumeTrue(
            !runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.CLOUDFOUNDRY_PLATFORM_TYPE)
                || runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"),
            "stream-app-cross-version-test: SKIP - CloudFoundry 2.6 and below!");

        Assumptions.assumeTrue(runtimeApps.isAppRegistered("ver-log", ApplicationType.sink, VERSION_3_0_1)
                && runtimeApps.isAppRegistered("ver-log", ApplicationType.sink, VERSION_2_1_5),
            "stream-app-cross-version-test: SKIP - required ver-log apps not registered!");

        logger.info("stream-app-cross-version-test: DEPLOY");

        int CURRENT_MANIFEST = 0;
        String RANDOM_SUFFIX = randomSuffix();

        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("app-cross-version-test" + RANDOM_SUFFIX)
            .definition("http | ver-log")
            .create()
            .deploy(new DeploymentPropertiesBuilder()
                .putAll(testDeploymentProperties("http"))
                .put("version.ver-log", VERSION_3_0_1)
                .build())) {
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            // Helper supplier to retrieve the ver-log version from the stream's current manifest.
            Supplier<String> currentVerLogVersion = () -> new SpringCloudDeployerApplicationManifestReader()
                .read(stream.manifest(CURRENT_MANIFEST))
                .stream()
                .filter(m -> m.getMetadata().get("name").equals("ver-log"))
                .map(m -> m.getSpec().getVersion())
                .findFirst().orElse("none");
            AwaitUtils.StreamLog verLogOffset = AwaitUtils.logOffset(stream, "ver-log");
            final String message1 = String.format("TEST MESSAGE 1-%s ", RANDOM_SUFFIX);
            runtimeApps.httpPost(stream.getName(), "http", message1);
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> AwaitUtils.hasInLog(verLogOffset, message1));
            assertThat(currentVerLogVersion.get()).isEqualTo(VERSION_3_0_1);
            assertThat(stream.history().size()).isEqualTo(1L);

            // UPDATE
            logger.info("stream-app-cross-version-test: UPDATE");

            stream.update(new DeploymentPropertiesBuilder().put("version.ver-log", VERSION_2_1_5).build());
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            final String message2 = String.format("TEST MESSAGE 2-%s ", RANDOM_SUFFIX);
            runtimeApps.httpPost(stream.getName(), "http", message2);
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> AwaitUtils.hasInLog(verLogOffset, message2));

            assertThat(currentVerLogVersion.get()).isEqualTo(VERSION_2_1_5);
            assertThat(stream.history().size()).isEqualTo(2);

            // ROLLBACK
            logger.info("stream-app-cross-version-test: ROLLBACK");

            stream.rollback(0);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            final String message3 = String.format("TEST MESSAGE 3-%s ", RANDOM_SUFFIX);
            runtimeApps.httpPost(stream.getName(), "http", message3);
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> AwaitUtils.hasInLog(verLogOffset, message3));
            assertThat(currentVerLogVersion.get()).isEqualTo(VERSION_3_0_1);
            assertThat(stream.history().size()).isEqualTo(3);
            logger.info("stream-app-cross-version-test: UNDEPLOY");
        }

        // DESTROY
        logger.info("stream-app-cross-version-test: DESTROY");

        assertThat(Optional.ofNullable(dataFlowOperations.streamOperations().list().getMetadata())
            .orElse(new PagedModel.PageMetadata(0, 0, 0))
            .getTotalElements()).isEqualTo(0L);
    }

    private void awaitStarting(Stream stream, AwaitUtils.StreamLog offset) {
        final long startErrorCheck = System.currentTimeMillis() + 30_000L;
        Awaitility.await("Deployment starting for " + stream.getName())
            .failFast(() -> {
                if (System.currentTimeMillis() > startErrorCheck) {
                    AwaitUtils.hasErrorInLog(offset);
                }
            })
            .until(() -> {
                logger.debug("awaitStarting:{}:{}", stream.getName(), stream.getStatus());
                try {
                    return starting.contains(stream.getStatus());
                } catch (DataFlowClientException x) {
                    logger.debug("awaitStarting:ignoring:" + x);
                    return false;
                }
            });
    }

    private void awaitDeployed(Stream stream, AwaitUtils.StreamLog offset) {
        final long startErrorCheck = System.currentTimeMillis() + 30_000L;
        Awaitility.await("Deployment for " + stream.getName())
            .failFast(() -> {
                if (System.currentTimeMillis() >= startErrorCheck) {
                    AwaitUtils.hasErrorInLog(offset);
                }
            })
            .until(() -> {
                try {
                    return stream.getStatus().equals(DEPLOYED);
                } catch (DataFlowClientException x) {
                    if (System.currentTimeMillis() > startErrorCheck) {
                        throw x;
                    }
                    return false;
                }
            });
    }

    @Test
    public void streamLifecycle() {
        streamLifecycleHelper(1, s -> {
        });
    }

    @Test
    public void streamLifecycleWithTwoInstance() {
        final int numberOfInstancePerApp = 2;
        streamLifecycleHelper(numberOfInstancePerApp, stream -> {
            Map<StreamApplication, Map<String, String>> streamApps = stream.runtimeApps();
            assertThat(streamApps.size()).isEqualTo(2);
            for (Map<String, String> instanceMap : streamApps.values()) {
                assertThat(instanceMap.size()).isEqualTo(numberOfInstancePerApp); // every apps should have 2 instances.
            }
        });
    }

    private void streamLifecycleHelper(int appInstanceCount, Consumer<Stream> streamAssertions) {
        logger.info("stream-lifecycle-test: DEPLOY");
        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("lifecycle-test" + randomSuffix())
            .definition("time | log --log.name='TEST' --log.expression='TICKTOCK - TIMESTAMP: '.concat(payload)")
            .create()
            .deploy(new DeploymentPropertiesBuilder()
                .putAll(testDeploymentProperties("log"))
                .put("deployer.*.count", Integer.toString(appInstanceCount))
                .build())) {
            logger.info("stream-lifecycle-test: await deployment");
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            logger.info("stream-lifecycle-test:deployed");
            streamAssertions.accept(stream);

            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("log")).contains("TICKTOCK - TIMESTAMP:"));

            assertThat(stream.history().size()).isEqualTo(1L);
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(1).equals(DEPLOYED));

            assertThat(stream.logs()).contains("TICKTOCK - TIMESTAMP:");
            assertThat(stream.logs(app("log"))).contains("TICKTOCK - TIMESTAMP:");

            // UPDATE
            logger.info("stream-lifecycle-test: UPDATE");
            stream.update(new DeploymentPropertiesBuilder()
                .put("app.log.log.expression", "'Updated TICKTOCK - TIMESTAMP: '.concat(payload)")
                .put("app.*.management.endpoints.web.exposure.include", "*")
                .build());
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            streamAssertions.accept(stream);

            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("log")).contains("Updated TICKTOCK - TIMESTAMP:"));

            assertThat(stream.history().size()).isEqualTo(2);
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(1).equals(DELETED));
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(2).equals(DEPLOYED));

            // ROLLBACK
            logger.info("stream-lifecycle-test: ROLLBACK");
            stream.rollback(0);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            streamAssertions.accept(stream);

            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("log")).contains("TICKTOCK - TIMESTAMP:"));

            assertThat(stream.history().size()).isEqualTo(3);
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(1).equals(DELETED));
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(2).equals(DELETED));
            Awaitility.await()
                .until(() -> starting.contains(stream.history().get(3)));
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(3).equals(DEPLOYED));

            // UNDEPLOY
            logger.info("stream-lifecycle-test: UNDEPLOY");
            stream.undeploy();

            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.getStatus().equals(UNDEPLOYED));

            assertThat(stream.history().size()).isEqualTo(3);
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(1).equals(DELETED));
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(2).equals(DELETED));
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(3).equals(DELETED));

            assertThat(dataFlowOperations.streamOperations().list().getMetadata().getTotalElements()).isEqualTo(1L);
            // DESTROY
        }
        logger.info("stream-lifecycle-test: DESTROY");
        assertThat(dataFlowOperations.streamOperations().list().getMetadata().getTotalElements()).isEqualTo(0L);
    }

    @Test
    public void streamScaling() {
        logger.info("stream-scaling-test");
        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("stream-scaling-test")
            .definition("time | log --log.expression='TICKTOCK - TIMESTAMP: '.concat(payload)")
            .create()
            .deploy(testDeploymentProperties("log", "time"))) {
            logger.info("stream-scaling-test:await deployment");
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            logger.info("stream-scaling-test:deployed");
            final StreamApplication time = app("time");
            final StreamApplication log = app("log");

            Map<StreamApplication, Map<String, String>> streamApps = stream.runtimeApps();
            assertThat(streamApps.size()).isEqualTo(2);
            assertThat(streamApps.get(time).size()).isEqualTo(1);
            assertThat(streamApps.get(log).size()).isEqualTo(1);

            // Scale up log
            stream.scaleApplicationInstances(log, 2, Collections.emptyMap());
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            Awaitility.await().until(() -> stream.runtimeApps().get(log).size() == 2);

            assertThat(stream.getStatus()).isEqualTo(DEPLOYED);
            streamApps = stream.runtimeApps();
            assertThat(streamApps.size()).isEqualTo(2);
            assertThat(streamApps.get(time).size()).isEqualTo(1);
            assertThat(streamApps.get(log).size()).isEqualTo(2);
        }
    }

    @Test
    public void namedChannelDestination() {
        logger.info("stream-named-channel-destination-test");
        try (
            Stream httpStream = Stream.builder(dataFlowOperations)
                .name("http-destination-source")
                .definition("http > :LOG-DESTINATION")
                .create()
                .deploy(testDeploymentProperties("http"));
            Stream logStream = Stream.builder(dataFlowOperations)
                .name("log-destination-sink")
                .definition(":LOG-DESTINATION > log")
                .create()
                .deploy(testDeploymentProperties("log"))) {
            logger.info("namedChannelDestination:deploying:{}", logStream.getName());
            logger.info("namedChannelDestination:deploying:{}", httpStream.getName());
            AwaitUtils.StreamLog logOffset = AwaitUtils.logOffset(logStream);
            AwaitUtils.StreamLog httpOffset = AwaitUtils.logOffset(httpStream);
            awaitStarting(logStream, logOffset);
            awaitDeployed(logStream, logOffset);
            logger.info("namedChannelDestination:deployed:{}", logStream.getName());
            awaitDeployed(httpStream, httpOffset);
            logger.info("namedChannelDestination:deployed:{}", httpStream.getName());
            String message = "Unique Test message: " + new Random().nextInt();
            logger.info("namedChannelDestination:sending:{} to {}", message, httpStream.getName());
            runtimeApps.httpPost(httpStream.getName(), "http", message);
            logger.info("namedChannelDestination:sent:{} to {}", message, httpStream.getName());
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(httpOffset))
                .failFast(() -> AwaitUtils.hasErrorInLog(logOffset))
                .until(() -> logStream.logs(app("log")).contains(message));
            logger.info("namedChannelDestination:found:{} in {}", message, logStream.getName());
        }
    }

    @Test
    public void namedChannelTap() {
        logger.info("stream-named-channel-tap-test");
        try (
            Stream httpLogStream = Stream.builder(dataFlowOperations)
                .name("taphttp")
                .definition("http | log")
                .create()
                .deploy(testDeploymentProperties("http"));
            Stream tapStream = Stream.builder(dataFlowOperations)
                .name("tapstream")
                .definition(":taphttp.http > log")
                .create()
                .deploy(testDeploymentProperties("log"))) {
            logger.info("namedChannelTap:deploying:{}", httpLogStream.getName());
            logger.info("namedChannelTap:deploying:{}", tapStream.getName());
            AwaitUtils.StreamLog httpOffset = AwaitUtils.logOffset(httpLogStream);
            AwaitUtils.StreamLog tapOffset = AwaitUtils.logOffset(tapStream);
            awaitStarting(httpLogStream, httpOffset);
            awaitDeployed(httpLogStream, httpOffset);
            logger.info("namedChannelTap:deployed:{}", httpLogStream.getName());
            awaitDeployed(tapStream, tapOffset);
            logger.info("namedChannelTap:deployed:{}", tapStream.getName());

            String message = "Unique Test message: " + new Random().nextInt();
            logger.info("namedChannelTap:sending:{}:{}", httpLogStream.getName(), message);
            runtimeApps.httpPost(httpLogStream.getName(), "http", message);
            logger.info("namedChannelTap:sent:{}:{}", httpLogStream.getName(), message);
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(tapOffset))
                .until(() -> tapStream.logs(app("log")).contains(message));
        }
    }

    @Test
    public void namedChannelManyToOne() {
        logger.info("stream-named-channel-many-to-one-test");
        try (
            Stream logStream = Stream.builder(dataFlowOperations)
                .name("many-to-one")
                .definition(":MANY-TO-ONE-DESTINATION > log")
                .create()
                .deploy(testDeploymentProperties("log"));
            Stream httpStreamOne = Stream.builder(dataFlowOperations)
                .name("http-source-1")
                .definition("http > :MANY-TO-ONE-DESTINATION")
                .create()
                .deploy(testDeploymentProperties("http"));
            Stream httpStreamTwo = Stream.builder(dataFlowOperations)
                .name("http-source-2")
                .definition("http > :MANY-TO-ONE-DESTINATION")
                .create()
                .deploy(testDeploymentProperties("http"))) {
            AwaitUtils.StreamLog logOffset = AwaitUtils.logOffset(logStream);
            AwaitUtils.StreamLog httpOffsetOne = AwaitUtils.logOffset(httpStreamOne);
            AwaitUtils.StreamLog httpOffsetTwo = AwaitUtils.logOffset(httpStreamTwo);

            Awaitility.await()
                .until(() -> starting.contains(logOffset.getStatus()));
            Awaitility.await()
                .atMost(10L, TimeUnit.MINUTES)
                .failFast(() -> AwaitUtils.hasErrorInLog(logOffset))
                .until(() -> logStream.getStatus().equals(DEPLOYED));

            Awaitility.await()
                .until(() -> starting.contains(httpOffsetOne.getStatus()));
            awaitDeployed(httpStreamOne, httpOffsetOne);

            awaitStarting(httpStreamTwo, httpOffsetTwo);
            awaitDeployed(httpStreamTwo, httpOffsetTwo);

            final String messageOne = "Unique Test message: " + new Random().nextInt();
            runtimeApps.httpPost(httpStreamOne.getName(), "http", messageOne);

            Awaitility.await()
                .until(() -> AwaitUtils.hasInLog(logOffset, messageOne));

            final String messageTwo = "Unique Test message: " + new Random().nextInt();
            runtimeApps.httpPost(httpStreamTwo.getName(), "http", messageTwo);

            Awaitility.await()
                .until(() -> AwaitUtils.hasInLog(logOffset, messageTwo));

        }
    }

    @Test
    public void namedChannelDirectedGraph() {
        logger.info("stream-named-channel-directed-graph-test");
        try (
            Stream fooLogStream = Stream.builder(dataFlowOperations)
                .name("directed-graph-destination1")
                .definition(":foo > transform --expression=payload+'-foo' | log")
                .create()
                .deploy(testDeploymentProperties("log"));
            Stream barLogStream = Stream.builder(dataFlowOperations)
                .name("directed-graph-destination2")
                .definition(":bar > transform --expression=payload+'-bar' | log")
                .create()
                .deploy(testDeploymentProperties("log"));
            Stream httpStream = Stream.builder(dataFlowOperations)
                .name("directed-graph-http-source")
                .definition("http | router --expression=payload.contains('a')?'foo':'bar'")
                .create()
                .deploy(testDeploymentProperties("http"))) {
            logger.info("namedChannelDirectedGraph:deploying:{}", httpStream.getName());
            logger.info("namedChannelDirectedGraph:deploying:{}", fooLogStream.getName());
            logger.info("namedChannelDirectedGraph:deploying:{}", barLogStream.getName());
            AwaitUtils.StreamLog fooOffset = AwaitUtils.logOffset(fooLogStream);
            AwaitUtils.StreamLog barOffset = AwaitUtils.logOffset(barLogStream);
            AwaitUtils.StreamLog httpOffset = AwaitUtils.logOffset(httpStream);

            awaitStarting(fooLogStream, fooOffset);
            Awaitility.await()
                .atMost(10L, TimeUnit.MINUTES)
                .failFast(() -> AwaitUtils.hasErrorInLog(fooOffset))
                .until(() -> fooLogStream.getStatus().equals(DEPLOYED));

            logger.info("namedChannelDirectedGraph:deployed:{}", fooLogStream.getName());

            awaitStarting(barLogStream, barOffset);
            awaitDeployed(barLogStream, barOffset);
            logger.info("namedChannelDirectedGraph:deployed:{}", barLogStream.getName());

            awaitStarting(httpStream, httpOffset);
            awaitDeployed(httpStream, httpOffset);
            logger.info("namedChannelDirectedGraph:deployed:{}", httpStream.getName());

            AwaitUtils.StreamLog fooLogOffset = AwaitUtils.logOffset(fooLogStream, "log");
            AwaitUtils.StreamLog barLogOffset = AwaitUtils.logOffset(barLogStream, "log");

            runtimeApps.httpPost(httpStream.getName(), "http", "abcd");
            runtimeApps.httpPost(httpStream.getName(), "http", "defg");
            logger.info("namedChannelDirectedGraph:sent:defg -> {}", httpStream.getName());
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(fooLogOffset))
                .until(() -> AwaitUtils.hasInLog(fooLogOffset, "abcd-foo"));
            Awaitility.await()
                .failFast(() -> AwaitUtils.hasErrorInLog(barLogOffset))
                .until(() -> AwaitUtils.hasInLog(barLogOffset, "defg-bar"));
        }
    }

    @Test
    public void dataflowTaskLauncherSink() throws JsonProcessingException {
        if (this.runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.LOCAL_PLATFORM_TYPE)) {
            logger.warn("Skipping since it doesn't work local");
        } else {
            String dataflowTaskLauncherAppName = "dataflow-tasklauncher";

            String skipOnIncompatibleDataFlowVersion = dataflowTaskLauncherAppName + "-sink-test: SKIP - Dataflow version:"
                + runtimeApps.getDataflowServerVersion() + " is older than 2.9.0-SNAPSHOT!";
            if (!runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.9.0-SNAPSHOT")) {
                logger.warn(skipOnIncompatibleDataFlowVersion);
            }
            Assumptions.assumeTrue(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.9.0-SNAPSHOT"),
                skipOnIncompatibleDataFlowVersion);

            String skipOnMissingAppRegistration = dataflowTaskLauncherAppName + "-sink-test: SKIP - no "
                + dataflowTaskLauncherAppName + " app registered!";
            boolean isDataflowTaskLauncherAppRegistered = runtimeApps.isAppRegistered(dataflowTaskLauncherAppName,
                ApplicationType.sink);
            if (!isDataflowTaskLauncherAppRegistered) {
                logger.info(skipOnMissingAppRegistration);
            }
            Assumptions.assumeTrue(isDataflowTaskLauncherAppRegistered, skipOnMissingAppRegistration);

            DetailedAppRegistrationResource dataflowTaskLauncherRegistration = dataFlowOperations.appRegistryOperations()
                .info(dataflowTaskLauncherAppName, ApplicationType.sink, false);

            logger.info("{}-sink-test: {} [{}], DataFlow [{}]", dataflowTaskLauncherAppName, dataflowTaskLauncherAppName, dataflowTaskLauncherRegistration.getVersion(), runtimeApps.getDataflowServerVersion());

            String taskName = randomTaskName();
            try (Task task = Task.builder(dataFlowOperations)
                .name(taskName)
                .definition("testtimestamp")
                .description("Test timestamp task")
                .build()) {
                logger.info("dataflowTaskLauncherSink:deploying:{}", dataflowTaskLauncherAppName);
                try (Stream stream = Stream.builder(dataFlowOperations).name("tasklauncher-test")
                    .definition("http | " + dataflowTaskLauncherAppName
                        + " --trigger.initialDelay=100 --trigger.maxPeriod=1000 " +
                        "--spring.cloud.dataflow.client.serverUri=" + dataFlowClientProperties.getServerUri())
                    .create()
                    .deploy(testDeploymentProperties("http"))) {
                    AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);

                    awaitStarting(stream, offset);
                    awaitDeployed(stream, offset);

                    Map<String, Object> data = new HashMap<>();
                    data.put("name", taskName);
                    runtimeApps.httpPostJson(stream.getName(), "http", data);

                    AtomicLong launchId = new AtomicLong();
                    Awaitility.await()
                        .until(() -> task.executions().stream()
                            .filter(t -> t.getTaskName().equals(taskName)
                                && t.getTaskExecutionStatus() == TaskExecutionStatus.COMPLETE)
                            .findFirst()
                            .map(t -> launchId.getAndSet(t.getExecutionId())).isPresent());
                    long id = launchId.get();
                    assertThat(task.executions().size()).isEqualTo(1);
                    assertThat(task.execution(id).isPresent()).isTrue();
                    assertThat(task.execution(id).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // STREAM METRICS TESTS
    // -----------------------------------------------------------------------
    @Test
    public void analyticsCounterInflux() {

        if (!influxPresent()) {
            logger.info("stream-analytics-test: SKIP - no InfluxDB metrics configured!");
        }

        Assumptions.assumeTrue(influxPresent());

        if (!runtimeApps.isAppRegistered("analytics", ApplicationType.sink)) {
            logger.info("stream-analytics-influx-test: SKIP - no analytics app registered!");
        }

        Assumptions.assumeTrue(runtimeApps.isAppRegistered("analytics", ApplicationType.sink),
            "stream-analytics-test: SKIP - no analytics app registered!");

        logger.info("stream-analytics-influx-test");

        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("httpAnalyticsInflux")
            .definition(
                "http | analytics --analytics.name=my_http_analytics --analytics.tag.expression.msgSize=payload.length()")
            .create()
            .deploy(testDeploymentProperties("http"))) {
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);

            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            String message1 = "Test message 1"; // length 14
            String message2 = "Test message 2 with extension"; // length 29
            String message3 = "Test message 2 with double extension"; // length 36

            String httpAppUrl = runtimeApps.getApplicationInstanceUrl(stream.getName(), "http");
            runtimeApps.httpPost(stream.getName(), "http", message1);
            runtimeApps.httpPost(stream.getName(), "http", message2);
            runtimeApps.httpPost(stream.getName(), "http", message3);

            // Wait for ~1 min for Micrometer to send first metrics to Influx.
            Awaitility.await()
                .until(() -> !JsonPath
                    .parse(runtimeApps.httpGet(testProperties.getPlatform().getConnection().getInfluxUrl()
                        + "/query?db=myinfluxdb&q=SELECT * FROM \"my_http_analytics\""))
                    .read("$.results[0][?(@.series)].length()").toString().equals("[]"));

            // http://localhost:8086/query?db=myinfluxdb&q=SELECT%20%22count%22%20FROM%20%22spring_integration_send%22
            // http://localhost:8086/query?db=myinfluxdb&q=SHOW%20MEASUREMENTS

            // http://localhost:8086/query?db=myinfluxdb&q=SELECT%20value%20FROM%20%22message_my_http_counter%22%20GROUP%20BY%20%2A%20ORDER%20BY%20ASC%20LIMIT%201

            // http://localhost:8086/query?q=SHOW%20DATABASES
            JsonAssertions
                .assertThatJson(runtimeApps.httpGet(
                    testProperties.getPlatform().getConnection().getInfluxUrl() + "/query?q=SHOW DATABASES"))
                .inPath("$.results[0].series[0].values[1][0]")
                .isEqualTo("myinfluxdb");

            List<String> messageLengths = java.util.stream.Stream.of(message1, message2, message3)
                .map(s -> String.format("\"%s\"", s.length())).collect(Collectors.toList());

            // http://localhost:8086/query?db=myinfluxdb&q=SELECT%20%2A%20FROM%20%22my_http_counter%22
            String myHttpCounter = runtimeApps.httpGet(testProperties.getPlatform().getConnection().getInfluxUrl()
                + "/query?db=myinfluxdb&q=SELECT * FROM \"my_http_analytics\"");
            JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[0][7]")
                .isIn(messageLengths);
            JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[1][7]")
                .isIn(messageLengths);
            JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[2][7]")
                .isIn(messageLengths);
        }
    }

    @Test
    public void analyticsCounterPrometheus() throws IOException {

        if (!runtimeApps.isAppRegistered("analytics", ApplicationType.sink)) {
            logger.info("stream-analytics-prometheus-test: SKIP - no analytics app registered!");
        }

        Assumptions.assumeTrue(runtimeApps.isAppRegistered("analytics", ApplicationType.sink),
            "stream-analytics-test: SKIP - no analytics app registered!");

        if (!prometheusPresent()) {
            logger.info("stream-analytics-prometheus-test: SKIP - no Prometheus configured!");
        }
        Assumptions.assumeTrue(prometheusPresent());

        logger.info("stream-analytics-prometheus-test");

        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("httpAnalyticsPrometheus")
            .definition(
                "http | analytics --analytics.name=my_http_analytics --analytics.tag.expression.msgSize=payload.length()")
            .create()
            .deploy(testDeploymentProperties("http"))) {
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);

            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            String message1 = "Test message 1"; // length 14
            String message2 = "Test message 2 with extension"; // length 29
            String message3 = "Test message 2 with double extension"; // length 36

            runtimeApps.httpPost(stream.getName(), "http", message1);
            runtimeApps.httpPost(stream.getName(), "http", message2);
            runtimeApps.httpPost(stream.getName(), "http", message3);

            // Wait for ~1 min for Micrometer to send first metrics to Prometheus.
            Awaitility.await().until(() -> (int) JsonPath.parse(
                    runtimeApps.httpGet(testProperties.getPlatform().getConnection().getPrometheusUrl()
                        + "/api/v1/query?query=my_http_analytics_total"))
                .read("$.data.result.length()") > 0);

            JsonAssertions
                .assertThatJson(runtimeApps.httpGet(testProperties.getPlatform().getConnection().getPrometheusUrl()
                    + "/api/v1/query?query=my_http_analytics_total"))
                .isEqualTo(resourceToString("classpath:/my_http_analytics_total.json"));
        }
    }

    /**
     * For the purpose of testing, disable security, expose the all actuators, and configure
     * logfiles.
     *
     * @param externallyAccessibleApps names of the stream applications that need to be
     *                                 accessible by the test code. Such as http app to post, messages or apps that need
     *                                 to allow access to the actuator/logfile.
     * @return Deployment properties required for the deployment of all test pipelines.
     */
    protected Map<String, String> testDeploymentProperties(String... externallyAccessibleApps) {
        DeploymentPropertiesBuilder propertiesBuilder = new DeploymentPropertiesBuilder()
            .put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
            .put("app.*.logging.file", "/tmp/${PID}-test.log") // Keep it for Boot 2.x compatibility.
            .put("app.*.logging.file.name", "/tmp/${PID}-test.log")
            .put("app.*.endpoints.logfile.sensitive", "false")
            .put("app.*.endpoints.logfile.enabled", "true")
            .put("app.*.management.endpoints.web.exposure.include", "*")
            .put("app.*.spring.cloud.streamapp.security.enabled", "false");


        if (this.runtimeApps.getPlatformType().equalsIgnoreCase(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            propertiesBuilder.put("app.*.server.port", "8080");
            for (String appName : externallyAccessibleApps) {
                propertiesBuilder.put("deployer." + appName + ".kubernetes.createLoadBalancer", "true"); // requires
                // LoadBalancer
                // support
                // on the
                // platform
            }
        }

        return propertiesBuilder.build();
    }

    public static String resourceToString(String resourcePath) throws IOException {
        return StreamUtils.copyToString(new DefaultResourceLoader().getResource(resourcePath).getInputStream(),
            StandardCharsets.UTF_8);
    }

    protected boolean prometheusPresent() {
        return runtimeApps.isServicePresent(
            testProperties.getPlatform().getConnection().getPrometheusUrl() + "/api/v1/query?query=up");
    }

    protected boolean influxPresent() {
        return runtimeApps.isServicePresent(testProperties.getPlatform().getConnection().getInfluxUrl() + "/ping");
    }

    public static Condition<String> condition(Predicate predicate) {
        return new Condition<>(predicate, "");
    }

    protected StreamApplication app(String appName) {
        return new StreamApplication(appName);
    }

    // -----------------------------------------------------------------------
    // TASK TESTS
    // -----------------------------------------------------------------------
    public static final int EXIT_CODE_SUCCESS = 0;

    public static final int EXIT_CODE_ERROR = 1;

    public static final String TEST_VERSION_NUMBER = "2.0.2";

    private List<String> composedTaskLaunchArguments(String... additionalArguments) {
        // the dataflow-server-use-user-access-token=true argument is required COMPOSED tasks in
        // oauth2-protected SCDF installations and is ignored otherwise.
        List<String> commonTaskArguments = new ArrayList<>();
        commonTaskArguments.addAll(Arrays.asList("--dataflow-server-use-user-access-token=true"));
        commonTaskArguments.addAll(Arrays.asList(additionalArguments));
        return commonTaskArguments;
    }

    @Test
    @EnabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "local")
    public void runBatchRemotePartitionJobLocal() {
        logger.info("runBatchRemotePartitionJob - local");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder
            .name(randomTaskName())
            .definition("batch-remote-partition")
            .description("runBatchRemotePartitionJob - local")
            .build()) {

            long launchId = task.launch(Collections.EMPTY_MAP, composedTaskLaunchArguments("--platform=local"));

            Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.execution(launchId).isPresent()).isTrue();
            assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
        }
    }

    @Test
    public void taskMetricsPrometheus() throws IOException {
        if (!prometheusPresent()) {
            logger.info("task-metrics-test: SKIP - no metrics configured!");
        }

        Assumptions.assumeTrue(prometheusPresent());

        logger.info("task-metrics-test: Prometheus");

        // task-demo-metrics-prometheus source: https://bit.ly/3bUfzWh
        try (Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("task-demo-metrics-prometheus --task.demo.delay.fixed=0s")
            .description("Test task metrics")
            .build()) {

            // task launch id
            long launchId = task.launch(Arrays.asList("--spring.cloud.task.closecontext_enabled=false"));

            Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.execution(launchId).isPresent()).isTrue();
            assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            // All
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            URI qplUri = UriComponentsBuilder.fromHttpUrl(testProperties.getPlatform().getConnection()
                    .getPrometheusUrl()
                    + String.format(
                    "/api/v1/query?query=system_cpu_usage{service=\"task-application\",application=\"%s-%s\"}",
                    task.getTaskName(), launchId))
                .build().toUri();

            Supplier<String> pqlTaskMetricsQuery = () -> dataFlowOperations.getRestTemplate()
                .exchange(qplUri, HttpMethod.GET, null, String.class).getBody();

            // Wait for ~1 min for Micrometer to send first metrics to Prometheus.
            Awaitility.await().until(() -> (int) JsonPath.parse(pqlTaskMetricsQuery.get())
                .read("$.data.result.length()") > 0);

            JsonAssertions.assertThatJson(pqlTaskMetricsQuery.get())
                .isEqualTo(resourceToString("classpath:/task_metrics_system_cpu_usage.json"));
        }
    }

    @Test
    public void composedTask() {
        logger.info("task-composed-task-runner-test");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);

        try (Task task = taskBuilder
            .name(randomTaskName())
            .definition("a: testtimestamp && b: testtimestamp")
            .description("Test composedTask")
            .build()) {

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);

            // first launch
            long launchId1 = task.launch(composedTaskLaunchArguments());

            validateSuccessfulTaskLaunch(task, launchId1);

            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId1).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // second launch
            long launchId2 = task.launch(composedTaskLaunchArguments());

            Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.executionStatus(launchId2)).isEqualTo(TaskExecutionStatus.COMPLETE);
            assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(2);
                assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            assertThat(taskBuilder.allTasks().size()).isEqualTo(3);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
    }

    @Test
    public void multipleComposedTaskWithArguments() {
        logger.info("task-multiple-composed-task-with-arguments-test");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder
            .name(randomTaskName())
            .definition("a: testtimestamp && b: testtimestamp")
            .description("Test multipleComposedTaskWithArguments")
            .build()) {

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);

            // first launch
            long launchId1 = task.launch(composedTaskLaunchArguments("--increment-instance-enabled=true"));

            Awaitility.await().until(() -> task.executionStatus(launchId1) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.executionStatus(launchId1)).isEqualTo(TaskExecutionStatus.COMPLETE);
            assertThat(task.execution(launchId1).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId1).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // second launch
            long launchId2 = task.launch(composedTaskLaunchArguments("--increment-instance-enabled=true"));

            Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.executionStatus(launchId2)).isEqualTo(TaskExecutionStatus.COMPLETE);
            assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(2);
                assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            assertThat(task.jobExecutionResources().size()).isEqualTo(2);

            assertThat(taskBuilder.allTasks().size()).isEqualTo(3);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
    }

    @Test
    public void ctrLaunchTest() {
        logger.info("composed-task-ctrLaunch-test");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder
            .name(randomTaskName())
            .definition("a: testtimestamp && b: testtimestamp")
            .description("ctrLaunchTest")
            .build()) {

            assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList()))
                .hasSameElementsAs(fullTaskNames(task, "a", "b"));

            long launchId = task.launch(composedTaskLaunchArguments());

            Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);

            // Parent Task Successfully completed
            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.executionStatus(launchId)).isEqualTo(TaskExecutionStatus.COMPLETE);
            assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // Child tasks successfully completed
            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            // Attempt a job restart
            assertThat(task.executions().size()).isEqualTo(1);
            List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
            assertThat(jobExecutionIds.size()).isEqualTo(1);

            // There is an Error deserialization issue related to backward compatibility with SCDF
            // 2.6.x
            // The Exception thrown by the 2.6.x servers can not be deserialized by the
            // VndErrorResponseErrorHandler in 2.8+ clients.
            Assumptions.assumingThat(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"), () -> {
                Exception exception = assertThrows(DataFlowClientException.class, () -> {
                    dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0));
                });
                assertTrue(exception.getMessage().contains(" and state 'COMPLETED' is not restartable"));
            });
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
    }

    @Test
    public void ctrFailedGraph() {
        logger.info("composed-task-ctrFailedGraph-test");
        mixedSuccessfulFailedAndUnknownExecutions("ctrFailedGraph",
            "scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false && testtimestamp",
            TaskExecutionStatus.ERROR,
            emptyList(), // successful
            asList("scenario"), // failed
            asList("testtimestamp")); // not-run
    }

    @Test
    public void ctrSplit() {
        logger.info("composed-task-split-test");
        allSuccessfulExecutions("ComposedTask Split Test",
            "<t1:timestamp || t2:timestamp || t3:timestamp>",
            "t1", "t2", "t3");
    }

    @Test
    public void ctrSequential() {
        logger.info("composed-task-sequential-test");
        allSuccessfulExecutions("ComposedTask Sequential Test",
            "t1: testtimestamp && t2: testtimestamp && t3: testtimestamp",
            "t1", "t2", "t3");
    }

    @Test
    public void ctrSequentialTransitionAndSplitWithScenarioFailed() {
        logger.info("composed-task-SequentialTransitionAndSplitWithScenarioFailed-test");
        mixedSuccessfulFailedAndUnknownExecutions(
            "ComposedTask Sequential Transition And Split With Scenario Failed Test",
            "t1: testtimestamp && scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED'->t3: testtimestamp && <t4: testtimestamp || t5: testtimestamp> && t6: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("t1", "t3"), // successful
            asList("scenario"), // failed
            asList("t4", "t5", "t6")); // not-run
    }

    @Test
    public void ctrSequentialTransitionAndSplitWithScenarioOk() {
        logger.info("composed-task-SequentialTransitionAndSplitWithScenarioOk-test");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split With Scenario Ok Test",
            "t1: testtimestamp && t2: scenario 'FAILED'->t3: testtimestamp && <t4: testtimestamp || t5: testtimestamp> && t6: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("t1", "t2", "t4", "t5", "t6"), // successful
            emptyList(), // failed
            asList("t3")); // not-run
    }

    @Test
    public void ctrNestedSplit() {
        logger.info("composed-task-NestedSplit");
        allSuccessfulExecutions("ctrNestedSplit",
            "<<t1: testtimestamp || t2: testtimestamp > && t3: testtimestamp || t4: testtimestamp>",
            "t1", "t2", "t3", "t4");
    }

    @Test
    public void testEmbeddedFailedGraph() {
        logger.info("composed-task-EmbeddedFailedGraph-test");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Embedded Failed Graph Test",
            String.format(
                "a: testtimestamp && b:scenario  --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true && c: testtimestamp",
                randomJobName()),
            TaskExecutionStatus.ERROR,
            asList("a"), // successful
            asList("b"), // failed
            asList("c")); // not-run
    }

    @Test
    public void twoSplitTest() {
        logger.info("composed-task-twoSplit-test");
        allSuccessfulExecutions("twoSplitTest",
            "<t1: testtimestamp ||t2: testtimestamp||t3: testtimestamp> && <t4: testtimestamp||t5: testtimestamp>",
            "t1", "t2", "t3", "t4", "t5");
    }

    @Test
    public void sequentialAndSplitTest() {
        logger.info("composed-task-sequentialAndSplit-test");
        allSuccessfulExecutions("sequentialAndSplitTest",
            "<t1: testtimestamp && <t2: testtimestamp || t3: testtimestamp || t4: testtimestamp> && t5: testtimestamp>",
            "t1", "t2", "t3", "t4", "t5");
    }

    @Test
    public void sequentialTransitionAndSplitFailedInvalidTest() {
        logger.info("composed-task-sequentialTransitionAndSplitFailedInvalid-test");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split Failed Invalid Test",
            "t1: testtimestamp && b:scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t2: testtimestamp && t3: testtimestamp && t4: testtimestamp && <t5: testtimestamp || t6: testtimestamp> && t7: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("t1", "t2"), // successful
            asList("b"), // failed
            asList("t3", "t4", "t5", "t6", "t7")); // not-run
    }

    @Test
    public void sequentialAndSplitWithFlowTest() {
        logger.info("composed-task-sequentialAndSplitWithFlow-test");
        allSuccessfulExecutions("sequentialAndSplitWithFlowTest",
            "t1: testtimestamp && <t2: testtimestamp && t3: testtimestamp || t4: testtimestamp ||t5: testtimestamp> && t6: testtimestamp",
            "t1", "t2", "t3", "t4", "t5", "t6");
    }

    @Test
    public void sequentialAndFailedSplitTest() {
        logger.info("composed-task-sequentialAndFailedSplit-test");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder
            .name(randomTaskName())
            .definition(String.format(
                "t1: testtimestamp && <t2: testtimestamp ||b:scenario --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true || t3: testtimestamp> && t4: testtimestamp",
                randomJobName()))
            .description("sequentialAndFailedSplitTest")
            .build()) {

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(5);
            assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList()))
                .hasSameElementsAs(fullTaskNames(task, "b", "t1", "t2", "t3", "t4"));

            long launchId = task.launch(composedTaskLaunchArguments());

            if (runtimeApps.dataflowServerVersionLowerThan("2.8.0-SNAPSHOT")) {
                Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);
            } else {
                Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.ERROR);
            }

            // Parent Task
            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // Successful
            childTasksBySuffix(task, "t1", "t2", "t3").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            // Failed tasks
            childTasksBySuffix(task, "b").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode())
                    .isEqualTo(EXIT_CODE_ERROR);
            });

            // Not run tasks
            childTasksBySuffix(task, "t4").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(0);
            });

            // Parent Task
            assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);

            // restart job
            assertThat(task.executions().size()).isEqualTo(1);
            List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
            assertThat(jobExecutionIds.size()).isEqualTo(1);
            dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0));

            long launchId2 = task.executions().stream().mapToLong(TaskExecutionResource::getExecutionId).max()
                .getAsLong();

            Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.executionStatus(launchId2)).isEqualTo(TaskExecutionStatus.COMPLETE);
            assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            childTasksBySuffix(task, "b").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(2);
                assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            childTasksBySuffix(task, "t4").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            assertThat(task.jobExecutionResources().size()).isEqualTo(2);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
    }

    @Test
    public void failedBasicTransitionTest() {
        logger.info("composed-task-failedBasicTransition-test");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Failed Basic Transition Test",
            "b: scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp * ->t2: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("t1"), // successful
            asList("b"), // failed
            asList("t2")); // not-run
    }

    @Test
    public void successBasicTransitionTest() {
        logger.info("composed-task-successBasicTransition-test");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Success Basic Transition Test",
            "b: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp * ->t2: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("b", "t2"), // successful
            emptyList(), // failed
            asList("t1")); // not-run
    }

    @Test
    public void basicTransitionWithTransitionTest() {
        logger.info("composed-task-basicTransitionWithTransition-test");
        mixedSuccessfulFailedAndUnknownExecutions("basicTransitionWithTransitionTest",
            "b1: scenario  --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp  && b2: scenario --io.spring.launch-batch-job=false 'FAILED' -> t2: testtimestamp * ->t3: testtimestamp ",
            TaskExecutionStatus.COMPLETE,
            asList("b1", "b2", "t3"), // successful
            emptyList(), // failed
            asList("t1", "t2")); // not-run
    }

    @Test
    public void wildCardOnlyInLastPositionTest() {
        logger.info("composed-task-wildCardOnlyInLastPosition-test");
        mixedSuccessfulFailedAndUnknownExecutions("wildCardOnlyInLastPositionTest",
            "b1: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp  && b2: scenario --io.spring.launch-batch-job=false * ->t3: testtimestamp ",
            TaskExecutionStatus.COMPLETE,
            asList("b1", "b2", "t3"), // successful
            emptyList(), // failed
            asList("t1")); // not-run
    }

    @Test
    public void failedCTRRetryTest() {
        logger.info("composed-task-failedCTRRetry-test");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder
            .name(randomTaskName())
            .definition(String.format(
                "b1:scenario --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true && t1: testtimestamp",
                randomJobName()))
            .description("failedCTRRetryTest")
            .build()) {

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);
            assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList()))
                .hasSameElementsAs(fullTaskNames(task, "b1", "t1"));

            long launchId = task.launch(composedTaskLaunchArguments());

            if (runtimeApps.dataflowServerVersionLowerThan("2.8.0-SNAPSHOT")) {
                Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);
            } else {
                Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.ERROR);
            }

            // Parent Task
            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // Failed tasks
            childTasksBySuffix(task, "b1").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode())
                    .isEqualTo(EXIT_CODE_ERROR);
            });

            // Not run tasks
            childTasksBySuffix(task, "t1").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(0);
            });

            // Parent Task
            assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);

            // restart job
            assertThat(task.executions().size()).isEqualTo(1);
            List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
            assertThat(jobExecutionIds.size()).isEqualTo(1);
            dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0));

            long launchId2 = task.executions().stream().mapToLong(TaskExecutionResource::getExecutionId).max()
                .getAsLong();

            Awaitility.await().until(() -> task.executionStatus(launchId2) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.execution(launchId2).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            childTasksBySuffix(task, "b1").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(2);
                assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            childTasksBySuffix(task, "t1").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId2).get().getExitCode())
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            assertThat(task.jobExecutionResources().size()).isEqualTo(2);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);

    }

    @Test
    public void basicBatchSuccessTest() {
        // Verify Batch runs successfully
        logger.info("basic-batch-success-test");
        try (Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("scenario")
            .description("Test scenario batch app")
            .build()) {

            String stepName = randomStepName();
            List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
            // task first launch
            long launchId = task.launch(args);
            // Verify task
            validateSuccessfulTaskLaunch(task, launchId);

            // Verify that steps can be retrieved
            verifySuccessfulJobAndStepScenario(task, stepName);
        }
    }

    private List<String> createNewJobandStepScenario(String jobName, String stepName) {
        List<String> result = new ArrayList<>();
        result.add("--io.spring.jobName=" + jobName);
        result.add("--io.spring.stepName=" + stepName);
        return result;
    }

    private void validateSuccessfulTaskLaunch(Task task, long launchId) {
        validateSuccessfulTaskLaunch(task, launchId, 1);
    }

    private void validateSuccessfulTaskLaunch(Task task, long launchId, int sizeExpected) {
        Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);
        assertThat(task.executions().size()).isEqualTo(sizeExpected);
        assertThat(task.execution(launchId).isPresent()).isTrue();
        assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
    }

    private void verifySuccessfulJobAndStepScenario(Task task, String stepName) {
        assertThat(task.executions().size()).isEqualTo(1);
        List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
        assertThat(jobExecutionIds.size()).isEqualTo(1);
        // Verify that steps can be retrieved
        task.jobExecutionResources().stream().filter(
            jobExecution -> jobExecution.getName().equals(task.getTaskName())).forEach(jobExecutionResource -> {
            assertThat(jobExecutionResource.getStepExecutionCount()).isEqualTo(1);
            task.jobStepExecutions(jobExecutionResource.getExecutionId()).forEach(stepExecutionResource -> {
                assertThat(stepExecutionResource.getStepExecution().getStepName()).isEqualTo(stepName);
            });
        });
    }

    private String randomStepName() {
        return "step-" + randomSuffix();
    }

    @Test
    public void basicBatchSuccessRestartTest() {
        // Verify that batch restart on success fails
        try (Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("scenario")
            .description("Test scenario batch app")
            .build()) {

            String stepName = randomStepName();
            List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
            // task first launch
            long launchId = task.launch(args);
            // Verify task and Job
            validateSuccessfulTaskLaunch(task, launchId);
            verifySuccessfulJobAndStepScenario(task, stepName);

            // Attempt a job restart
            List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();

            // There is an Error deserialization issue related to backward compatibility with SCDF
            // 2.6.x
            // The Exception thrown by the 2.6.x servers can not be deserialized by the
            // VndErrorResponseErrorHandler in 2.8+ clients.
            Assumptions.assumingThat(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"), () -> {
                Exception exception = assertThrows(DataFlowClientException.class, () -> {
                    dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0));
                });
                assertTrue(exception.getMessage().contains(" and state 'COMPLETED' is not restartable"));
            });
        }
    }

    @Test
    public void basicBatchFailRestartTest() {
        // Verify Batch runs successfully
        logger.info("basic-batch-fail-restart-test");
        try (Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("scenario")
            .description("Test scenario batch app that will fail on first pass")
            .build()) {

            String stepName = randomStepName();
            List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
            args.add("--io.spring.failBatch=true");
            // task first launch
            long launchId = task.launch(args);
            // Verify task
            validateSuccessfulTaskLaunch(task, launchId);

            // Verify that batch app that fails can be restarted

            // Attempt a job restart
            List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
            // There is an Error deserialization issue related to backward compatibility with SCDF
            // 2.6.x
            // The Exception thrown by the 2.6.x servers can not be deserialized by the
            // VndErrorResponseErrorHandler in 2.8+ clients.
            Assumptions.assumingThat(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"), () -> {
                dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0));
                // Wait for job to start
                Awaitility.await().until(() -> task.jobExecutionResources().size() == 2);
                // Wait for task for the job to complete
                Awaitility.await().until(() -> task.executions().stream().findFirst().get()
                    .getTaskExecutionStatus() == TaskExecutionStatus.COMPLETE);
                assertThat(task.jobExecutionResources().size()).isEqualTo(2);
                List<JobExecutionResource> jobExecutionResources = task.jobInstanceResources().stream().findFirst()
                    .get().getJobExecutions().stream().collect(Collectors.toList());
                List<BatchStatus> batchStatuses = new ArrayList<>();
                jobExecutionResources.stream().forEach(
                    jobExecutionResource -> batchStatuses.add(jobExecutionResource.getJobExecution().getStatus()));
                assertThat(batchStatuses).contains(BatchStatus.FAILED);
                assertThat(batchStatuses).contains(BatchStatus.COMPLETED);
            });
        }
    }

    @Test
    public void testLaunchOfDefaultThenVersion() {
        // Scenario: I want to create a task app with 2 versions using default version
        // Given A task with 2 versions
        // And I create a task definition
        // When I launch task definition using default app version
        // And I launch task definition using version 2 of app
        // Then Both tasks should succeed
        // And It launches the specified version
        logger.info("multiple task app version test");
        minimumVersionCheck("testLaunchOfDefaultThenVersion");

        Task task = createTaskDefinition();

        long launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId);
        registerNewTimestampVersion();
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
        launchId = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launchId, 2);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER);
    }

    @Test
    public void testCreateTaskWithTwoVersionsLaunchDefaultVersion() {
        // Scenario: I want to create a task app with 2 versions using default version
        // Given A task with 2 versions
        // And I create a task definition
        // When I launch task definition using default app version
        // Then Task should succeed
        // And It launches the specified version
        minimumVersionCheck("testCreateTaskWithTwoVersionsLaunchDefaultVersion");
        registerNewTimestampVersion();
        Task task = createTaskDefinition();
        long launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
    }

    @Test
    public void testLaunchOfNewVersionThenPreviousVersion() {
        // Scenario: I want to create a task app with 2 versions run new version then default
        // Given A task with 2 versions
        // And I create a task definition
        // And I launch task definition using version 2 of app
        // When I launch task definition using version 1 of app
        // Then Task should succeed
        // And It launches the specified version
        minimumVersionCheck("testLaunchOfNewVersionThenDefault");
        registerNewTimestampVersion();
        Task task = createTaskDefinition();
        long launchId = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launchId);
        assertThat(task.execution(launchId).get().getResourceUrl()).contains(TEST_VERSION_NUMBER);

        launchId = task.launch(Collections.singletonMap("version.testtimestamp", CURRENT_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launchId, 2);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
    }

    @Test
    public void testWhenNoVersionIsSpecifiedPreviousVersionShouldBeUsed() {
        // Scenario: When no version is specified previous used version should be used.
        // Given A task with 2 versions
        // And I create a task definition
        // And I launch task definition using version 2 of app
        // When I launch task definition using no app version
        // Then Task should succeed
        // And It launches the version 2 of app
        minimumVersionCheck("testWhenNoVersionIsSpecifiedPreviousVersionShouldBeUsed");
        registerNewTimestampVersion();
        Task task = createTaskDefinition();
        long launchId = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launchId);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER);

        launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId, 2);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER, 2);
    }

    @Test
    public void testCreateTaskWithOneVersionLaunchInvalidVersion() {
        // Scenario: I want to create a task app with 1 version run invalid version
        // Given A task with 1 versions
        // And I create a task definition
        // When I launch task definition using version 2 of app
        // Then Task should fail
        minimumVersionCheck("testCreateTaskWithOneVersionLaunchInvalidVersion");
        Task task = createTaskDefinition();
        assertThatThrownBy(() -> task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null))
            .isInstanceOf(DataFlowClientException.class).hasMessageContaining("Unknown task app: testtimestamp");
    }

    @Test
    public void testInvalidVersionUsageShouldNotAffectSubsequentDefaultLaunch() {
        // Scenario: Invalid version usage should not affect subsequent default launch
        // Given A task with 1 versions
        // And I create a task definition
        // And I launch task definition using version 2 of app
        // When I launch task definition using default app version
        // Then Task should succeed
        // And It launches the specified version
        minimumVersionCheck("testInvalidVersionUsageShouldNotAffectSubsequentDefaultLaunch");
        Task task = createTaskDefinition();
        assertThatThrownBy(() -> task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null))
            .isInstanceOf(DataFlowClientException.class)
            .hasMessageContaining("Unknown task app: testtimestamp");

        long launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId, 1);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER, 1);
    }

    @Test
    public void testDeletePreviouslyUsedVersionShouldFailIfRelaunched() {
        // Scenario: Deleting a previously used version should fail if relaunched.
        // Given A task with 2 versions
        // And I create a task definition
        // And I launch task definition using version 2 of app
        // And I unregister version 2 of app
        // When I launch task definition using version 2 of app
        // Then Task should fail
        minimumVersionCheck("testDeletePreviouslyUsedVersionShouldFailIfRelaunched");

        registerNewTimestampVersion();
        Task task = createTaskDefinition();

        long launchId = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launchId);
        resetTimestampVersion();
        assertThatThrownBy(() -> task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null))
            .isInstanceOf(DataFlowClientException.class).hasMessageContaining("Unknown task app: testtimestamp");
    }

    @Test
    public void testChangingTheAppDefaultVersionRunningBetweenChangesShouldBeSuccessful() {
        // Scenario: Changing the app default version and running between changes should be
        // successful
        // Given A task with 2 versions
        // And I create a task definition
        // And I launch task definition using default app version
        // And I set the default to version 2 of the app
        // When I launch task definition using default app version
        // Then Task should succeed
        // And The version for the task execution should be version 2
        minimumVersionCheck("testChangingTheAppDefaultVersionRunningBetweenChangesShouldBeSuccessful");
        Task task = createTaskDefinition();

        long launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);

        registerNewTimestampVersion();
        setDefaultVersionForTimestamp(TEST_VERSION_NUMBER);
        launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId, 2);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER);
    }

    @Test
    public void testRollingBackDefaultToPreviousVersionAndRunningShouldBeSuccessful() {
        // Scenario: Rolling back default to previous version and running should be successful
        // Given A task with 2 versions
        // And I create a task definition
        // And I launch task definition using default app version
        // And I set the default to version 2 of the app
        // And I launch task definition using default app version
        // And I set the default to version 1 of the app
        // When I create a task definition
        // And I launch task definition using default app version
        // Then Task should succeed
        // And The version for the task execution should be version 1
        minimumVersionCheck("testRollingBackDefaultToPreviousVersionAndRunningShouldBeSuccessful");
        registerNewTimestampVersion();
        Task task = createTaskDefinition();
        long launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);

        setDefaultVersionForTimestamp(TEST_VERSION_NUMBER);
        launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId, 2);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER);

        task = createTaskDefinition();
        setDefaultVersionForTimestamp(CURRENT_VERSION_NUMBER);
        launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
    }

    @Test
    public void testUnregisteringAppShouldPreventTaskDefinitionLaunch() {
        // Scenario: Unregistering app should prevent task definition launch
        // Given A task with 1 versions
        // And I create a task definition
        // And I launch task definition using default app version
        // And I unregister version 1 of app
        // When I launch task definition using default app version
        // Then Task should fail
        minimumVersionCheck("testUnregisteringAppShouldPreventTaskDefinitionLaunch");
        Task task = createTaskDefinition();
        long launchId = task.launch();
        validateSuccessfulTaskLaunch(task, launchId);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
        AppRegistryOperations appRegistryOperations = this.dataFlowOperations.appRegistryOperations();
        appRegistryOperations.unregister("testtimestamp", ApplicationType.task, CURRENT_VERSION_NUMBER);

        assertThatThrownBy(() -> task.launch()).isInstanceOf(DataFlowClientException.class)
            .hasMessageContaining("Unknown task app: testtimestamp");
    }

    private Task createTaskDefinition() {
        return createTaskDefinition("testtimestamp");
    }

    private Task createTaskDefinition(String definition) {
        String taskDefName = randomTaskName();
        return Task.builder(dataFlowOperations)
            .name(taskDefName)
            .definition(definition)
            .description(String.format("Test task definition %s using for app definition\"%s\"", taskDefName,
                definition))
            .build();
    }

    private void minimumVersionCheck(String testName) {
        Assumptions.assumeTrue(!runtimeApps.dataflowServerVersionLowerThan("2.8.0"),
            testName + ": SKIP - SCDF 2.7.x and below!");
    }

    private void registerNewTimestampVersion() {
        registerTimestamp(TEST_VERSION_NUMBER);
    }

    private void registerTimestamp(String versionNumber) {
        if (this.runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            registerTask("testtimestamp", "docker:springcloudtask/timestamp-task", versionNumber);
        } else {
            registerTask("testtimestamp", "maven://io.spring:timestamp-task", versionNumber);
        }
    }

    private void setDefaultVersionForTimestamp(String version) {
        AppRegistryOperations appRegistryOperations = this.dataFlowOperations.appRegistryOperations();
        appRegistryOperations.makeDefault("testtimestamp", ApplicationType.task, version);
    }

    private void assertTaskRegistration(String name) {
        try {
            AppRegistryOperations appRegistryOperations = this.dataFlowOperations.appRegistryOperations();
            DetailedAppRegistrationResource resource = appRegistryOperations.info(name, ApplicationType.task, false);
            logger.info("assertTaskRegistration:{}:{}", name, resource.getLinks());
        } catch (DataFlowClientException x) {
            fail(x.getMessage());
        }
    }

    private void resetTimestampVersion() {
        AppRegistryOperations appRegistryOperations = this.dataFlowOperations.appRegistryOperations();
        try {
            appRegistryOperations.unregister("testtimestamp", ApplicationType.task, TEST_VERSION_NUMBER);
        } catch (DataFlowClientException x) {
            if (!x.toString().contains("not be found")) {
                logger.error("resetTimestampVersion:Exception:" + x);
            }
        }
        if (this.runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            registerTask("testtimestamp", "docker:springcloudtask/timestamp-task", CURRENT_VERSION_NUMBER);
        } else {
            registerTask("testtimestamp", "maven://io.spring:timestamp-task", CURRENT_VERSION_NUMBER);
        }
        setDefaultVersionForTimestamp(CURRENT_VERSION_NUMBER);
    }

    private void validateSpecifiedVersion(Task task, String version) {
        validateSpecifiedVersion(task, version, 1);
    }

    private void validateSpecifiedVersion(Task task, String version, int countExpected) {
        assertThat(task.executions().stream().filter(
                taskExecutionResource -> taskExecutionResource.getResourceUrl().contains(version))
            .collect(Collectors.toList()).size()).isEqualTo(countExpected);
    }

    @Test
    public void basicTaskWithPropertiesTest() {
        logger.info("basic-task-with-properties-test");
        String testPropertyKey = "app.testtimestamp.test-prop-key";
        String testPropertyValue = "test-prop-value";

        try (Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("testtimestamp")
            .description("Test testtimestamp app that will use properties")
            .build()) {
            String stepName = randomStepName();
            List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
            // task first launch
            long launchId = task.launch(Collections.singletonMap(testPropertyKey, testPropertyValue), args);
            // Verify task
            validateSuccessfulTaskLaunch(task, launchId);
            long launchId1 = task.launch(args);
            Awaitility.await().until(() -> task.executionStatus(launchId1) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task
                .executions().stream().filter(taskExecutionResource -> taskExecutionResource
                    .getDeploymentProperties().containsKey(testPropertyKey))
                .collect(Collectors.toList()).size()).isEqualTo(2);

        }
    }

    @Test
    public void taskLaunchInvalidTaskDefinition() {
        logger.info("task-launch-invalid-task-definition");
        Exception exception = assertThrows(DataFlowClientException.class, () -> {
            Task.builder(dataFlowOperations)
                .name(randomTaskName())
                .definition("foobar")
                .description("Test scenario with invalid task definition")
                .build();
        });
        assertTrue(exception.getMessage().contains("The 'task:foobar' application could not be found."));
    }

    @Test
    public void taskLaunchWithArguments() {
        // Launch task with args and verify that they are being used.
        // Verify Batch runs successfully
        logger.info("basic-batch-success-test");
        final String argument = "--testtimestamp.format=YYYY";
        try (Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("testtimestamp")
            .description("Test launch apps with arguments app")
            .build()) {

            String stepName = randomStepName();
            List<String> baseArgs = createNewJobandStepScenario(task.getTaskName(), stepName);
            List<String> args = new ArrayList<>(baseArgs);
            args.add(argument);
            // task first launch
            long launchId = task.launch(args);
            // Verify first launch
            validateSuccessfulTaskLaunch(task, launchId);
            // relaunch task with no args and it should not re-use old.
            long launchId1 = task.launch(baseArgs);
            Awaitility.await().until(() -> task.executionStatus(launchId1) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.executions().stream().filter(execution -> execution.getArguments().contains(argument))
                .collect(Collectors.toList()).size()).isEqualTo(1);
        }

    }

    @Test
    public void taskDefinitionDelete() {
        logger.info("task-definition-delete");
        final String taskName;
        try (Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("scenario")
            .description("Test scenario batch app that will fail on first pass")
            .build()) {
            taskName = task.getTaskName();
            String stepName = randomStepName();
            List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
            long launchId = task.launch(args);

            validateSuccessfulTaskLaunch(task, launchId);
            assertThat(dataFlowOperations.taskOperations().list().getContent().size()).isEqualTo(1);
        }
        verifyTaskDefAndTaskExecutionCount(taskName, 0, 1);
    }

    @Test
    public void taskDefinitionDeleteWithCleanup() {
        Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("scenario")
            .description("Test scenario batch app that will fail on first pass")
            .build();
        String stepName = randomStepName();
        List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
        // task first launch
        long launchId = task.launch(args);
        // Verify task
        validateSuccessfulTaskLaunch(task, launchId);
        // verify task definition is gone and executions are removed
        this.dataFlowOperations.taskOperations().destroy(task.getTaskName(), true);
        verifyTaskDefAndTaskExecutionCount(task.getTaskName(), 0, 0);
    }

    @Test
    public void testDeleteSingleTaskExecution() {
        // Scenario: I want to delete a single task execution
        // Given A task definition exists
        // And 1 task execution exist
        // When I delete a task execution
        // Then It should succeed
        // And I will not see the task executions
        minimumVersionCheck("testDeleteSingleTaskExecution");
        try (Task task = createTaskDefinition()) {
            List<Long> launchIds = createTaskExecutionsForDefinition(task, 1);
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            safeCleanupTaskExecution(task, launchIds.get(0));
            verifyAllSpecifiedTaskExecutions(task, launchIds, false);
        }
    }

    @Test
    public void testDeleteMultipleTaskExecution() {
        // Scenario: I want to delete 3 task executions
        // Given A task definition exists
        // And 4 task execution exist
        // When I delete 3 task executions
        // Then They should succeed
        // And I will see the remaining task execution
        minimumVersionCheck("testDeleteMultipleTaskExecution");
        try (Task task = createTaskDefinition()) {
            List<Long> launchIds = createTaskExecutionsForDefinition(task, 4);
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            long retainedLaunchId = launchIds.get(3);
            launchIds.stream().filter(launchId -> launchId != retainedLaunchId).forEach(
                launchId -> {
                    safeCleanupTaskExecution(task, launchId);
                    assertThat(task.execution(launchId).isPresent()).isFalse();
                });
            assertThat(task.execution(retainedLaunchId).isPresent()).isTrue();
        }
    }

    @Test
    public void testDeleteAllTaskExecutionsShouldClearAllTaskExecutions() {
        // Scenario: Delete all task executions should clear all task executions
        // Given A task definition exists
        // And 4 task execution exist
        // When I delete all task executions
        // Then It should succeed
        // And I will not see the task executions
        minimumVersionCheck("testDeleteAllTaskExecutionsShouldClearAllTaskExecutions");
        try (Task task = createTaskDefinition()) {
            List<Long> launchIds = createTaskExecutionsForDefinition(task, 4);
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            safeCleanupAllTaskExecutions(task);
            verifyAllSpecifiedTaskExecutions(task, launchIds, false);
        }
    }

    @Test
    public void testDataFlowUsesLastAvailableTaskExecutionForItsProperties() {
        // Scenario: Task Launch should use last available task execution for its properties
        // Given A task definition exists
        // And 2 task execution exist each having different properties
        // When I launch task definition using default app version
        // Then It should succeed
        // And The task execution will contain the properties from both task executions
        minimumVersionCheck("testDataFlowUsesLastAvailableTaskExecutionForItsProperties");
        try (Task task = createTaskDefinition()) {
            List<Long> firstLaunchIds = createTaskExecutionsForDefinition(task,
                Collections.singletonMap("app.testtimestamp.firstkey", "firstvalue"), 1);
            verifyAllSpecifiedTaskExecutions(task, firstLaunchIds, true);

            long secondLaunchId = task.launch();
            assertThat(task.execution(secondLaunchId).isPresent()).isTrue();
            validateSuccessfulTaskLaunch(task, secondLaunchId, 2);
            Optional<TaskExecutionResource> taskExecution = task.execution(secondLaunchId);
            Map<String, String> properties = taskExecution.get().getAppProperties();
            assertThat(properties.containsKey("firstkey")).isTrue();
        }
    }

    @Test
    public void testDataFlowUsesAllPropertiesRegardlessIfPreviousExecutionWasDeleted() {
        // Scenario: Task Launch should use last available task execution for its properties after
        // deleting previous version
        // Given A task definition exists
        // And 2 task execution exist each having different properties
        // And I delete the last task execution
        // When I launch task definition using default app version
        // Then It should succeed
        // And The task execution will contain the properties from the last available task
        minimumVersionCheck("testDataFlowUsesAllPropertiesRegardlessIfPreviousExecutionWasDeleted");
        try (Task task = createTaskDefinition()) {
            List<Long> firstLaunchIds = createTaskExecutionsForDefinition(task,
                Collections.singletonMap("app.testtimestamp.firstkey", "firstvalue"), 1);
            verifyAllSpecifiedTaskExecutions(task, firstLaunchIds, true);
            long secondLaunchId = task.launch(Collections.singletonMap("app.testtimestamp.secondkey", "secondvalue"),
                Collections.emptyList());
            assertThat(task.execution(secondLaunchId).isPresent()).isTrue();
            validateSuccessfulTaskLaunch(task, secondLaunchId, 2);
            safeCleanupTaskExecution(task, secondLaunchId);
            assertThat(task.execution(secondLaunchId).isPresent()).isFalse();

            long thirdLaunchId = task.launch(Collections.singletonMap("app.testtimestamp.thirdkey", "thirdvalue"),
                Collections.emptyList());
            assertThat(task.execution(thirdLaunchId).isPresent()).isTrue();
            validateSuccessfulTaskLaunch(task, thirdLaunchId, 2);
            Optional<TaskExecutionResource> taskExecution = task.execution(thirdLaunchId);
            Map<String, String> properties = taskExecution.get().getAppProperties();
            assertThat(properties.containsKey("firstkey")).isTrue();
            assertThat(properties.containsKey("secondkey")).isFalse();
            assertThat(properties.containsKey("thirdkey")).isTrue();

        }
    }

    @Test
    public void testDeletingComposedTaskExecutionDeletesAllItsChildTaskExecutions() {
        // Deleting a Composed Task Execution deletes all of its child task executions
        // Given A composed task definition exists of "AAA && BBB"
        // And 1 task execution exist
        // And I delete the last task execution
        // Then It should succeed
        // And I will not see the composed task executions
        minimumVersionCheck("testDeletingComposedTaskExecutionDeletesAllItsChildTaskExecutions");
        try (Task task = createTaskDefinition("AAA: testtimestamp && BBB: testtimestamp")) {
            List<Long> launchIds = createTaskExecutionsForDefinition(task, 1);
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            Optional<TaskExecutionResource> aaaExecution = task.composedTaskChildExecution("AAA");
            Optional<TaskExecutionResource> bbbExecution = task.composedTaskChildExecution("BBB");
            assertThat(aaaExecution.isPresent()).isTrue();
            assertThat(bbbExecution.isPresent()).isTrue();
            safeCleanupTaskExecution(task, launchIds.get(0));
            verifyAllSpecifiedTaskExecutions(task, launchIds, false);
            aaaExecution = task.composedTaskChildExecution("AAA");
            bbbExecution = task.composedTaskChildExecution("BBB");
            assertThat(aaaExecution.isPresent()).isFalse();
            assertThat(bbbExecution.isPresent()).isFalse();
        }

    }

    @Test
    public void testDeletingBatchTaskExecutionDeletesAllOfItsBatchRecords() {
        // Given A batch task definition exists
        // And 1 task execution exist
        // When I delete the last task execution
        // Then It should succeed
        // And I will not see the task executions
        // And I will not see the batch executions
        minimumVersionCheck("testDeletingBatchTaskExecutionDeletesAllOfItsBatchRecords");
        try (Task task = createTaskDefinition("testtimestamp-batch")) {
            List<Long> launchIds = Collections.singletonList(task.launch(Collections.emptyMap(),
                Collections.singletonList("testKey=" + task.getTaskName())));
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            validateSuccessfulTaskLaunch(task, launchIds.get(0), 1);

            List<Long> jobExecutionIds = task.execution(launchIds.get(0)).get().getJobExecutionIds();
            assertThat(jobExecutionIds.size()).isEqualTo(2);
            assertThat(task.jobStepExecutions(jobExecutionIds.get(0)).equals(1));
            safeCleanupTaskExecution(task, launchIds.get(0));
            verifyAllSpecifiedTaskExecutions(task, launchIds, false);
            assertThatThrownBy(() -> task.jobStepExecutions(jobExecutionIds.get(0)))
                .isInstanceOf(DataFlowClientException.class).hasMessageContaining("No JobExecution with id=");
        }
    }

    @Test
    public void testRestartingBatchTaskExecutionThatHasBeenDeleted() {
        // Restarting a Batch Task Execution that has been deleted
        // Given A batch task definition exists
        // And 1 task execution exist
        // And I delete the last task execution
        // When I restart the batch job
        // And The batch job will fail
        minimumVersionCheck("testRestartingBatchTaskExecutionThatHasBeenDeleted");
        try (Task task = createTaskDefinition("testtimestamp-batch")) {
            List<Long> launchIds = Collections.singletonList(task.launch(Collections.emptyMap(),
                Collections.singletonList("testKey=" + task.getTaskName())));
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            validateSuccessfulTaskLaunch(task, launchIds.get(0), 1);

            List<Long> jobExecutionIds = task.execution(launchIds.get(0)).get().getJobExecutionIds();
            assertThat(jobExecutionIds.size()).isEqualTo(2);
            assertThat(task.jobStepExecutions(jobExecutionIds.get(0)).equals(1));
            safeCleanupTaskExecution(task, launchIds.get(0));
            assertThatThrownBy(() -> this.dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0)))
                .isInstanceOf(DataFlowClientException.class)
                .hasMessageContaining("There is no JobExecution with id=");
        }

    }

    private List<Long> createTaskExecutionsForDefinition(Task task, int executionCount) {
        return createTaskExecutionsForDefinition(task, Collections.emptyMap(), executionCount);
    }

    private List<Long> createTaskExecutionsForDefinition(Task task, Map<String, String> properties,
                                                         int executionCount) {
        List<Long> launchIds = new ArrayList<>();
        for (int i = 0; i < executionCount; i++) {
            launchIds.add(task.launch(properties, Collections.emptyList()));
            assertThat(task.execution(launchIds.get(i)).isPresent()).isTrue();
            validateSuccessfulTaskLaunch(task, launchIds.get(i), i + 1);
        }
        return launchIds;
    }

    private void verifyAllSpecifiedTaskExecutions(Task task, List<Long> launchIds, boolean isPresent) {
        launchIds.stream().forEach(
            launchId -> {
                if (isPresent) {
                    assertThat(task.execution(launchId).isPresent()).isTrue();
                } else {
                    assertThat(task.execution(launchId).isPresent()).isFalse();
                }
            });
    }

    private void verifyTaskDefAndTaskExecutionCount(String taskName, int taskDefCount, int taskExecCount) {
        assertThat(dataFlowOperations.taskOperations().executionList().getContent().stream()
            .filter(taskExecution -> taskExecution.getTaskName() != null
                && taskExecution.getTaskName().equals(taskName))
            .collect(Collectors.toList()).size()).isEqualTo(taskExecCount);
        assertThat(dataFlowOperations.taskOperations().list().getContent().size()).isEqualTo(taskDefCount);
    }

    private void allSuccessfulExecutions(String taskDescription, String taskDefinition, String... childLabels) {
        mixedSuccessfulFailedAndUnknownExecutions(taskDescription, taskDefinition,
            TaskExecutionStatus.COMPLETE, asList(childLabels), emptyList(), emptyList());
    }

    private void mixedSuccessfulFailedAndUnknownExecutions(String taskDescription, String taskDefinition,
                                                           TaskExecutionStatus parentTaskExecutionStatus,
                                                           List<String> successfulTasks, List<String> failedTasks, List<String> unknownTasks) {

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder
            .name(randomTaskName())
            .definition(taskDefinition)
            .description(taskDescription)
            .build()) {

            ArrayList<String> allTasks = new ArrayList<>(successfulTasks);
            allTasks.addAll(failedTasks);
            allTasks.addAll(unknownTasks);

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(allTasks.size());
            assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList()))
                .as("verify composedTaskChildTasks is the same as all tasks")
                .hasSameElementsAs(fullTaskNames(task, allTasks.toArray(new String[0])));

            long launchId = task.launch(composedTaskLaunchArguments());

            if (runtimeApps.dataflowServerVersionLowerThan("2.8.0-SNAPSHOT")) {
                Awaitility.await().until(() -> task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE);
            } else {
                Awaitility.await().until(() -> task.executionStatus(launchId) == parentTaskExecutionStatus);
            }

            // Parent Task
            assertThat(task.executions().size())
                .as("verify exactly one execution")
                .isEqualTo(1);
            assertThat(task.execution(launchId).get().getExitCode())
                .as("verify successful execution of parent task")
                .isEqualTo(EXIT_CODE_SUCCESS);
            task.executions().forEach(execution -> assertThat(execution.getExitCode())
                .as("verify successful execution of parent task").isEqualTo(EXIT_CODE_SUCCESS));

            // Successful tasks
            childTasksBySuffix(task, successfulTasks.toArray(new String[0])).forEach(childTask -> {
                assertThat(childTask.executions().size())
                    .as("verify each child task ran once").isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode())
                    .as("verify each child task has a successful parent").isEqualTo(EXIT_CODE_SUCCESS);
            });

            // Failed tasks
            childTasksBySuffix(task, failedTasks.toArray(new String[0])).forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode())
                    .isEqualTo(EXIT_CODE_ERROR);
            });

            // Not run tasks
            childTasksBySuffix(task, unknownTasks.toArray(new String[0])).forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(0);
            });

            // Parent Task
            assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
    }

    private List<String> fullTaskNames(Task task, String... childTaskNames) {
        return java.util.stream.Stream.of(childTaskNames)
            .map(cn -> task.getTaskName() + "-" + cn.trim()).collect(Collectors.toList());
    }

    private List<Task> childTasksBySuffix(Task task, String... suffixes) {
        return java.util.stream.Stream.of(suffixes)
            .map(suffix -> task.composedTaskChildTaskByLabel(suffix).get()).collect(Collectors.toList());
    }

    private void safeCleanupAllTaskExecutions(Task task) {
        doSafeCleanupTasks(() -> task.cleanupAllTaskExecutions());
    }

    private void safeCleanupTaskExecution(Task task, long taskExecutionId) {
        doSafeCleanupTasks(() -> task.cleanupTaskExecution(taskExecutionId));
    }

    private void doSafeCleanupTasks(Runnable cleanupOperation) {
        try {
            cleanupOperation.run();
        } catch (DataFlowClientException ex) {
            if (ex.getMessage().contains("(reason: pod does not exist)") || ex.getMessage()
                .contains("(reason: job does not exist)")) {
                logger.warn("Unable to cleanup task executions: " + ex.getMessage());
            } else {
                throw ex;
            }
        }
    }

    private static String randomTaskName() {
        return "task-" + randomSuffix();
    }

    private static String randomJobName() {
        return "job-" + randomSuffix();
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    private static List<String> asList(String... names) {
        return Arrays.asList(names);
    }

    private static List<String> emptyList() {
        return Collections.emptyList();
    }

    // -----------------------------------------------------------------------
    //                     STREAM  CONFIG SERVER (PCF ONLY)
    // -----------------------------------------------------------------------
    @Test
    @EnabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry")
    @DisabledIfSystemProperty(named = "SKIP_CLOUD_CONFIG", matches = "true")
    public void streamWithConfigServer() {

        logger.info("stream-server-config-test");

        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("TICKTOCK-config-server")
            .definition("time | log")
            .create()
            .deploy(new DeploymentPropertiesBuilder()
                .putAll(testDeploymentProperties("log", "time"))
                .put("app.log.spring.profiles.active", "test")
                .put("deployer.log.cloudfoundry.services", "cloud-config-server")
                .put("app.log.spring.cloud.config.name", "MY_CONFIG_TICKTOCK_LOG_NAME")
                .build())) {
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);

            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            Awaitility.await("Source not started")
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("time")).contains("Started TimeSource"));
            Awaitility.await("Sink not started")
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("log")).contains("Started LogSink"));
            Awaitility.await("No output found")
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("log")).contains("TICKTOCK CLOUD CONFIG - TIMESTAMP:"));
        }
    }
}
