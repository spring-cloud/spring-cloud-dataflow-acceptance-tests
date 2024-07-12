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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.batch.core.BatchStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.LaunchResponse;
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
import org.springframework.cloud.dataflow.rest.client.support.VersionUtils;
import org.springframework.cloud.dataflow.rest.resource.DetailedAppRegistrationResource;
import org.springframework.cloud.dataflow.rest.resource.JobExecutionThinResource;
import org.springframework.cloud.dataflow.rest.resource.LaunchResponseResource;
import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.StreamDeploymentResource;
import org.springframework.cloud.dataflow.rest.resource.TaskDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionStatus;
import org.springframework.cloud.dataflow.rest.resource.about.AboutResource;
import org.springframework.cloud.dataflow.schema.AppBootSchemaVersion;
import org.springframework.cloud.skipper.domain.SpringCloudDeployerApplicationManifestReader;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpMethod;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Christian Tzolov
 * @author Corneil du Plessis
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.Random.class)
@Import(DataFlowOperationsATConfiguration.class)
@DockerCompose
class DataFlowAT extends CommonTestBase {
    private static final String TESTING_ONE_TWO_THREE = "Testing, Testing, One, Two, Three...";
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
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(15L));
        Awaitility.setDefaultPollDelay(Duration.ofSeconds(5L));
        Awaitility.setDefaultTimeout(Duration.ofMinutes(5L));
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
            logger.error("after:" + e.getMessage(), e);
        } finally {
            try {
                dataFlowOperations.taskOperations().list().forEach(taskDefinitionResource -> {
                    logger.info("Destroying task {} and execution history", taskDefinitionResource.getName());
                    try {
                        dataFlowOperations.taskOperations().destroy(taskDefinitionResource.getName(), true);
                    } catch (DataFlowClientException x) {
                        logger.warn("destroy:" + taskDefinitionResource.getName() + ":" + x);
                    }
                });
                logger.info("Destroyed all tasks and execution history");
            } catch (Exception e) {
                logger.error("after:" + e.getMessage(), e);
            }
        }
        logger.debug("after:end");
    }

    @Test
    @Order(Integer.MIN_VALUE)
    @Tag("always")
    public void aboutTestInfo() {
        logger.info("Available platforms: " + dataFlowOperations.streamOperations()
            .listPlatforms()
            .stream()
            .map(d -> String.format("[name: %s, type: %s]", d.getName(), d.getType()))
            .collect(Collectors.joining()));
        logger.info(String.format("Selected platform: [name: %s, type: %s, Dataflow-Version: %s]",
            runtimeApps.getPlatformName(),
            runtimeApps.getPlatformType(),
            runtimeApps.getDataflowServerVersion()));
        logger.info("Wait until at least 60 apps are registered in SCDF");
        Awaitility.await("About Test")
            .until(() -> dataFlowOperations.appRegistryOperations().list().getMetadata().getTotalElements() >= 60L);
    }

    @Test
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "kubernetes")
    @Tag("smoke")
    @Tag("always")
    public void applicationMetadataMavenTests() {
        logger.info("application-metadata-maven-test:start");

        // Maven app with metadata
        DetailedAppRegistrationResource mavenAppWithJarMetadata = dataFlowOperations.appRegistryOperations().info("file", ApplicationType.sink, false);
        assertThat(mavenAppWithJarMetadata.getOptions()).describedAs("mavenAppWithJarMetadata").hasSize(8);

        // Maven app without metadata
        dataFlowOperations.appRegistryOperations()
            .register("maven-app-without-metadata",
                ApplicationType.sink,
                "maven://org.springframework.cloud.stream.app:file-sink-kafka:3.0.1",
                null,
                AppBootSchemaVersion.BOOT2,
                true);
        DetailedAppRegistrationResource mavenAppWithoutMetadata = dataFlowOperations.appRegistryOperations()
            .info("maven-app-without-metadata", ApplicationType.sink, false);
        assertThat(mavenAppWithoutMetadata.getOptions()).describedAs("mavenAppWithoutMetadata").hasSize(8);
        // unregister the test apps
        dataFlowOperations.appRegistryOperations().unregister("maven-app-without-metadata", ApplicationType.sink);
        logger.info("application-metadata-maven-test:end");
    }

    @Test
    @Tag("always")
    @Tag("smoke")
    public void applicationMetadataDockerTests() {
        logger.info("application-metadata-docker-test:start");

        // Docker app with container image metadata
        dataFlowOperations.appRegistryOperations()
            .register("docker-app-with-container-metadata",
                ApplicationType.source,
                "docker:springcloudstream/time-source-kafka:3.2.1",
                "maven://org.springframework.cloud.stream.app:time-source-kafka:jar:metadata:3.2.1",
                AppBootSchemaVersion.BOOT2,
                true);
        DetailedAppRegistrationResource dockerAppWithContainerMetadata = dataFlowOperations.appRegistryOperations()
            .info("docker-app-with-container-metadata", ApplicationType.source, false);
        assertThat(dockerAppWithContainerMetadata.getOptions()).hasSize(7);

        // Docker app with container image metadata with escape characters.
        dataFlowOperations.appRegistryOperations()
            .register("docker-app-with-container-metadata-escape-chars",
                ApplicationType.source,
                "docker:springcloudstream/http-source-rabbit:3.2.1",
                "maven://org.springframework.cloud.stream.app:http-source-rabbit:jar:metadata:3.2.1",
                AppBootSchemaVersion.BOOT2,
                true);
        DetailedAppRegistrationResource dockerAppWithContainerMetadataWithEscapeChars = dataFlowOperations.appRegistryOperations()
            .info("docker-app-with-container-metadata-escape-chars", ApplicationType.source, false);
        assertThat(dockerAppWithContainerMetadataWithEscapeChars.getOptions()).hasSize(6);

        // Docker app without metadata
        dataFlowOperations.appRegistryOperations()
            .register("docker-app-without-metadata",
                ApplicationType.sink,
                "docker:springcloudstream/log-sink-kafka:3.2.1",
                "maven://org.springframework.cloud.stream.app:log-sink-kafka:jar:metadata:3.2.1",
                AppBootSchemaVersion.BOOT2,
                true);
        DetailedAppRegistrationResource dockerAppWithoutMetadata = dataFlowOperations.appRegistryOperations()
            .info("docker-app-without-metadata", ApplicationType.sink, false);
        assertThat(dockerAppWithoutMetadata.getOptions()).hasSize(3);

        // Docker app with jar metadata
        dataFlowOperations.appRegistryOperations()
            .register("docker-app-with-jar-metadata",
                ApplicationType.sink,
                "docker:springcloudstream/file-sink-kafka:3.2.1",
                "maven://org.springframework.cloud.stream.app:file-sink-kafka:jar:metadata:3.2.1",
                AppBootSchemaVersion.BOOT2,
                true);
        DetailedAppRegistrationResource dockerAppWithJarMetadata = dataFlowOperations.appRegistryOperations()
            .info("docker-app-with-jar-metadata", ApplicationType.sink, false);
        assertThat(dockerAppWithJarMetadata.getOptions()).hasSize(8);

        // unregister the test apps
        dataFlowOperations.appRegistryOperations().unregister("docker-app-with-container-metadata", ApplicationType.source);
        dataFlowOperations.appRegistryOperations().unregister("docker-app-with-container-metadata-escape-chars", ApplicationType.source);
        dataFlowOperations.appRegistryOperations().unregister("docker-app-without-metadata", ApplicationType.sink);
        dataFlowOperations.appRegistryOperations().unregister("docker-app-with-jar-metadata", ApplicationType.sink);
        logger.info("application-metadata-docker-test:start");
    }

    @Test
    @EnabledIfSystemProperty(named = "BINDER", matches = "rabbit")
    @Tag("group3")
    public void multipleStreamApps() {
        logger.info("multiple-stream-apps-test:start");
        if (this.runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            registerApp("kitchen", "docker:springcloudstream/scdf-app-kitchen:1.0.0-SNAPSHOT", AppBootSchemaVersion.BOOT2);
            registerApp("customer", "docker:springcloudstream/scdf-app-customer:1.0.0-SNAPSHOT", AppBootSchemaVersion.BOOT2);
            registerApp("waitron", "docker:springcloudstream/scdf-app-waitron:1.0.0-SNAPSHOT", AppBootSchemaVersion.BOOT2);
        } else {
            registerApp("kitchen", "maven:io.spring:scdf-app-kitchen:1.0.0-SNAPSHOT", AppBootSchemaVersion.BOOT2);
            registerApp("customer", "maven:io.spring:scdf-app-customer:1.0.0-SNAPSHOT", AppBootSchemaVersion.BOOT2);
            registerApp("waitron", "maven:io.spring:scdf-app-waitron:1.0.0-SNAPSHOT", AppBootSchemaVersion.BOOT2);
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
            .name("restaurant-test" + randomSuffix())
            .definition("kitchen || waitron || customer").create();
        logger.info("multipleStreamApps:deploy:restaurant-test");
        Stream destroy = null;
        try {
            final Stream stream = streamDefinition.deploy(new DeploymentPropertiesBuilder().putAll(testDeploymentProperties("waitron"))
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
                .build());
            logger.info("multipleStreamApps:waiting-for-deployment:restaurant-test");
            destroy = stream;
            final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            awaitDeployed(stream, offset);
            logger.info("multipleStreamApps:waiting for acceptPayment:restaurant-test");
            awaitValueInLog(stream,  app("waitron"), "acceptPayment");
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
        logger.info("multiple-stream-apps-test:end");
    }

    @Test
    @Tag("group2")
    public void timestampTask() {
        logger.info("task-timestamp-test:start");
        assertTaskRegistration("testtimestamp", AppBootSchemaVersion.BOOT2);
        try (Task task = Task.builder(dataFlowOperations).name(randomTaskName()).definition("testtimestamp").description("Test timestamp task").build()) {

            // task first launch
            LaunchResponseResource launch1 = task.launch();

            validateSuccessfulTaskLaunch(task, launch1.getExecutionId(), launch1.getSchemaTarget());

            // task second launch
            LaunchResponseResource launch2 = task.launch();

            Awaitility.await("timestampTask:testtimestamp COMPLETE")
                .until(() -> task.executionStatus(launch2.getExecutionId(), launch2.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(2);
            Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch2.getExecutionId(), launch2.getSchemaTarget());
            assertThat(taskExecutionResource).isPresent();
            assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            // All
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));
        }
        logger.info("task-timestamp-test:end");
    }

    @Test
    @Tag("group2")
    public void timestampTaskBoot3() {
        logger.info("task-timestamp3-test:start");
        if (supportBoot3Jobs()) {
            assertTaskRegistration("testtimestamp3", AppBootSchemaVersion.BOOT3);
            try (Task task = Task.builder(dataFlowOperations).name(randomTaskName()).definition("testtimestamp3").description("Test timestamp task").build()) {

                // task first launch
                LaunchResponseResource launch1 = task.launch();

                validateSuccessfulTaskLaunch(task, launch1.getExecutionId(), launch1.getSchemaTarget());

                // task second launch
                LaunchResponseResource launch2 = task.launch();

                Awaitility.await("timestampTaskBoot3:testtimestamp3 COMPLETE")
                    .until(() -> task.executionStatus(launch2.getExecutionId(), launch2.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
                assertThat(task.executions().size()).isEqualTo(2);
                Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch2.getExecutionId(), launch2.getSchemaTarget());
                assertThat(taskExecutionResource).isPresent();
                assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

                // All
                task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));
            }
        } else {
            logger.warn("task-timestamp-test3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
        logger.info("task-timestamp-test3:end");
    }

    @Test
    @EnabledIfSystemProperty(named = "SCDF_CR_TEST", matches = "true")
    @Tag("group4")
    public void githubContainerRegistryTests() {
        logger.info("github-container-registry-tests:start");
        containerRegistryTests("github-log-sink", "docker:ghcr.io/tzolov/log-sink-rabbit:3.1.0-SNAPSHOT", AppBootSchemaVersion.BOOT2);
        logger.info("github-container-registry-tests:start");
    }

    @Test
    @EnabledIfSystemProperty(named = "SCDF_CR_TEST", matches = "true")
    @Tag("group4")
    public void azureContainerRegistryTests() {
        logger.info("azure-container-registry-tests:start");
        containerRegistryTests("azure-log-sink", "docker:scdftest.azurecr.io/springcloudstream/log-sink-rabbit:3.1.0-SNAPSHOT", AppBootSchemaVersion.BOOT2);
        logger.info("azure-container-registry-tests:end");
    }

    @Test
    @EnabledIfSystemProperty(named = "SCDF_CR_TEST", matches = "true")
    @Tag("group4")
    public void harborContainerRegistryTests() {
        logger.info("harbor-container-registry-tests:start");
        containerRegistryTests("harbor-log-sink",
            "docker:projects.registry.vmware.com/scdf/scdftest/log-sink-rabbit:3.1.0-SNAPSHOT",
            AppBootSchemaVersion.BOOT2);
        logger.info("harbor-container-registry-tests:end");
    }

    private void containerRegistryTests(String appName, String appUrl, AppBootSchemaVersion bootVersion) {
        logger.info("application-metadata-{}-container-registry-test:start", appName);

        // Docker app with container image metadata
        dataFlowOperations.appRegistryOperations().register(appName, ApplicationType.sink, appUrl, null, bootVersion, true);
        DetailedAppRegistrationResource dockerAppWithContainerMetadata = dataFlowOperations.appRegistryOperations().info(appName, ApplicationType.sink, false);
        assertThat(dockerAppWithContainerMetadata.getOptions()).hasSize(3);

        // unregister the test apps
        dataFlowOperations.appRegistryOperations().unregister(appName, ApplicationType.sink);
        logger.info("application-metadata-{}-container-registry-test:done", appName);
    }

    // -----------------------------------------------------------------------
    // PLATFORM TESTS
    // -----------------------------------------------------------------------
    @Test
    @Tag("always")
    public void featureInfo() {
        logger.info("platform-feature-info-test:start");
        AboutResource about = dataFlowOperations.aboutOperation().get();
        assertThat(about.getFeatureInfo().isAnalyticsEnabled()).isTrue();
        assertThat(about.getFeatureInfo().isStreamsEnabled()).isTrue();
        assertThat(about.getFeatureInfo().isTasksEnabled()).isTrue();
        logger.info("platform-feature-info-test:end");
    }

    @Test
    @Tag("always")
    public void appsCount() {
        logger.info("platform-apps-count-test:start");
        assertThat(dataFlowOperations.appRegistryOperations().list().getMetadata().getTotalElements()).isGreaterThanOrEqualTo(60L);
        logger.info("platform-apps-count-test:end");
    }

    // -----------------------------------------------------------------------
    // STREAM TESTS
    // -----------------------------------------------------------------------

    /**
     * Target Data Flow platform to use for the testing:
     * <a href="https://dataflow.spring.io/docs/concepts/architecture/#platforms">https://dataflow.spring.io/docs/concepts/architecture/#platforms</a>
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
    @Tag("group6")
    public void streamReDeploy() {
        logger.info("stream-redeploy-test:start");
        for (int i = 0; i < 2; i++) {
            try (Stream stream = Stream.builder(dataFlowOperations)
                .name("redeploy-test" + randomSuffix())
                .definition("http | log")
                .create()
                .deploy(testDeploymentProperties("http", "log"))) {
                final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
                logger.info("stream-redeploy-test:deploying:{}", stream.getName());
                awaitStarting(stream, offset);
                awaitDeployed(stream, offset);
                logger.info("stream-redeploy-test:deployed:{}", stream.getName());
                stream.undeploy();
                Awaitility.await("streamReDeploy:" + stream.getName() + " is UNDEPLOYED")
                    .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                    .until(() -> stream.getStatus().equals(UNDEPLOYED));
                logger.info("stream-redeploy-test:undeployed:{}", stream.getName());
            }
        }
        logger.info("stream-redeploy-test:end");
    }
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry", disabledReason = "Temporary disabling due to HTTP 403 issues")
    @Test
    @Tag("group6")
    @Tag("smoke")
    public void streamTransform() {
        logger.info("stream-transform:start");
        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("transform-test" + randomSuffix())
            .definition("http | transform --spel.function.expression=payload.toUpperCase() | log")
            .create()
            .deploy(testDeploymentProperties("http"))) {
            final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            logger.info("stream-transform-test:deploying:{}", stream.getName());
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            logger.info("stream-transform-test:deployed:{}", stream.getName());

            String prefix = "Unique Test message: ";
            String message = prefix + new Random().nextInt();

            runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);
            runtimeApps.httpPost(stream.getName(), "http", message);
            logger.info("stream-transform-test:sent:{}:{}", stream.getName(), message);

            awaitValueInLog(stream, app("log"), prefix.toUpperCase());
            awaitValueInLog(stream, app("log"), message.toUpperCase());
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("stream-transform-test:done");
    }

    @Disabled("Temporary disabling due to HTTP 403 issues")
    @Test
    @Tag("group6")
    @Tag("smoke")
    public void streamScriptEncoding() {
        final String dsl = "http | script --script-processor.language=groovy --script-processor.script=payload+'嗨你好世界' | log";
        logger.info("stream-script-encoding:start");

        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("script-test" + randomSuffix())
            .definition(dsl)
            .create()
            .deploy(testDeploymentProperties("http"))
        ) {
            final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            logger.info("stream-script-test:deploying:{}", stream.getName());
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            logger.info("stream-script-test:deployed:{}", stream.getName());
            String message = "Unique Test message: " + new Random().nextInt();
            runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);
            runtimeApps.httpPost(stream.getName(), "http", message);
            logger.info("stream-script-test:sent:{}:{}", stream.getName(), message);
            final AwaitUtils.StreamLog logOffset = AwaitUtils.logOffset(stream, "log");
            awaitValueInLog(stream, app("log"), message);
            AwaitUtils.hasInLog(logOffset, message + "嗨你好世界");
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("stream-script-test:done");
    }

    private static String lastLines(String log, int lines) {
        int startIndex = -1;
        String logPortion = log;
        for (int i = 0; i < lines; i++) {
            int index = logPortion.lastIndexOf('\n');
            if (index > 0) {
                startIndex = index;
                logPortion = logPortion.substring(0, index);
            }
        }
        if (startIndex > 0) {
            return log.substring(startIndex);
        }
        return log;
    }

    @Test
    @Tag("group1")
    // TODO: remove when logs are available per partition
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "kubernetes")
    public void streamPartitioning() {
        logger.info("stream-partitioning-test:start (aka. WoodChuckTests)");
        StreamDefinition streamDefinition = Stream.builder(dataFlowOperations)
            .name("partitioning-test" + randomSuffix())
            .definition("http | splitter --splitter.expression=payload.split(' ') | log")
            .create();
        final int partitions = 3;
        try (Stream stream = streamDefinition.deploy(new DeploymentPropertiesBuilder().putAll(testDeploymentProperties("http", "log"))
            .put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
            // Create 2 log instances with partition key computed from the payload.
            .put("deployer.log.count", Integer.toString(partitions))
            .put("app.splitter.producer.partitionKeyExpression", "payload")
            .put("app.splitter.producer.partitionCount", Integer.toString(partitions))
            .put("app.log.spring.cloud.stream.kafka.bindings.input.consumer.autoRebalanceEnabled", "false")
            .put("app.log.logging.pattern.level", "WOODCHUCK-${INSTANCE_INDEX:${CF_INSTANCE_INDEX:${spring.cloud.stream.instanceIndex:666}}} %5p")
            .build())) {
            logger.info("streamPartitioning:deploying:{}", stream.getName());
            final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            logger.info("streamPartitioning:deployed:{}", stream.getName());
            String message = "How much wood would a woodchuck chuck if a woodchuck could chuck wood";
            runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);
            logger.info("streamPartitioning:sending:{}:{}", stream.getName(), message);
            runtimeApps.httpPost(stream.getName(), "http", message);
            logger.info("streamPartitioning:sent:{}:{}", stream.getName(), message);
            final Map<String, List<String>> expectations = new HashMap<>();
            Arrays.stream(message.split(" ")).forEach(msg -> {
                int partition = Math.abs(msg.hashCode() % partitions);
                String key = "WOODCHUCK-" + partition;
                List<String> list = expectations.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    list.add(key);
                    expectations.put(key, list);
                }
                if (!list.contains(msg)) {
                    list.add(msg);
                }
            });
            final int maxWords = expectations.values().stream().mapToInt(List::size).max().orElse(partitions);
            expectations.values().forEach(expectation -> logger.info("Expectation:{}", expectation));
            assertThat(expectations.size()).isEqualTo(partitions);
            Awaitility.await("expected values in logs:" + expectations)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> {
                    Map<String, String> logMap = runtimeApps.applicationInstanceLogs(stream.getName(), "log");
                    if (logMap != null) {
                        Collection<String> logs = logMap.values().stream().map(s -> lastLines(s, maxWords + 1)).collect(Collectors.toList());
                        logger.info("streamPartitioning:logs:{}", logs);
                        return (logs.size() == partitions) && logs.stream()
                            // partition order is undetermined
                            .map(log -> expectations.values().stream().anyMatch(expect -> expect.stream().allMatch(log::contains)))
                            .reduce(Boolean::logicalAnd)
                            .orElse(false);
                    } else {
                        return false;
                    }
                });
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("stream-partitioning-test:done (aka. WoodChuckTests)");
    }

    @Test
    @Tag("group3")
    // TODO: remove when logs are available per partition
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "kubernetes")
    public void streamPartitioningNamed() {
        logger.info("stream-partitioning-named-test:start (aka. WoodChuckTests)");

        final String message = "How much wood would a woodchuck chuck if a woodchuck could chuck wood";
        final int partitions = 3;
        final Map<String, List<String>> expectations = new HashMap<>();
        Arrays.stream(message.split(" ")).forEach(msg -> {
            int partition = Math.abs(msg.hashCode() % partitions);
            String key = "WOODCHUCK-" + partition;
            List<String> list = expectations.get(key);
            if (list == null) {
                list = new ArrayList<>();
                list.add(key);
                expectations.put(key, list);
            }
            if (!list.contains(msg)) {
                list.add(msg);
            }
        });
        final int maxWords = expectations.values().stream().mapToInt(List::size).max().orElse(partitions);
        expectations.values().forEach(expectation -> logger.info("Expectation:{}", expectation));
        assertThat(expectations.size()).isEqualTo(partitions);
        String RANDOM_SUFFIX = randomSuffix();
        String topic = "topic1" + RANDOM_SUFFIX;
        StreamDefinition streamDefinition = Stream.builder(dataFlowOperations)
            .name("partitioning-named-test" + RANDOM_SUFFIX)
            .definition("http | splitter --splitter.expression=payload.split(' ') > :" + topic)
            .create();
        StreamDefinition logDefinition = Stream.builder(dataFlowOperations)
            .name("partitioning-named-log" + RANDOM_SUFFIX)
            .definition(":" + topic + " > log")
            .create();

        try (Stream stream = streamDefinition.deploy(new DeploymentPropertiesBuilder().putAll(testDeploymentProperties("http"))
            .put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
            .put("app.splitter.producer.partitionKeyExpression", "payload")
            .put("app.splitter.producer.partitionCount", Integer.toString(partitions))
            .build())) {
            try (Stream logStream = logDefinition.deploy(new DeploymentPropertiesBuilder().putAll(testDeploymentProperties("log"))
                .put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
                .put("deployer.log.count", Integer.toString(partitions))
                .put("app.log.spring.cloud.stream.kafka.bindings.input.consumer.autoRebalanceEnabled", "false")
                .put("app.log.logging.pattern.level", "WOODCHUCK-${INSTANCE_INDEX:${CF_INSTANCE_INDEX:${spring.cloud.stream.instanceIndex:666}}} %5p")
                .build())) {
                logger.info("streamPartitioningNamed:deploying:{}", stream.getName());
                final AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
                logger.info("streamPartitioningNamed:deploying:{}", logStream.getName());
                final AwaitUtils.StreamLog logOffset = AwaitUtils.logOffset(logStream);
                awaitStarting(stream, offset);
                awaitStarting(logStream, logOffset);
                awaitDeployed(stream, offset);
                logger.info("streamPartitioningNamed:deployed:{}", stream.getName());
                awaitDeployed(logStream, logOffset);
                logger.info("streamPartitioningNamed:deployed:{}", logStream.getName());
                logger.info("streamPartitioningNamed:sending:{}:{}", stream.getName(), message);
                runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);
                runtimeApps.httpPost(stream.getName(), "http", message);
                logger.info("streamPartitioningNamed:sent:{}:{}", stream.getName(), message);

                Awaitility.await("expectation in logs:" + expectations)
                    .failFast(() -> AwaitUtils.hasErrorInLog(logOffset))
                    .until(() -> {
                        Map<String, String> logMap = runtimeApps.applicationInstanceLogs(logStream.getName(), "log");
                        if (logMap != null) {
                            Collection<String> logs = logMap.values().stream().map(s -> lastLines(s, maxWords + 1)).collect(Collectors.toList());
                            logger.info("streamPartitioningNamed:logs:{}", logs);
                            return (logs.size() == partitions) && logs.stream()
                                // partition order is undetermined
                                .map(log -> expectations.values().stream().anyMatch(expect -> expect.stream().allMatch(log::contains)))
                                .reduce(Boolean::logicalAnd)
                                .orElse(false);
                        } else {
                            return false;
                        }
                    });
            }
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("stream-partitioning-test:done (aka. WoodChuckTests)");
    }

    @Test
    @Order(Integer.MIN_VALUE + 10)
    @Disabled // all tests are excluding this test
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry")
    @Tag("group3")
    @Tag("smoke")
    public void streamAppCrossVersion() {
        logger.info("stream-app-cross-version:start");
        final String VERSION_2_1_5 = "2.1.5.RELEASE";
        final String VERSION_3_0_1 = "3.0.1";

        Assumptions.assumeTrue(!runtimeApps.getPlatformType()
                .equals(RuntimeApplicationHelper.CLOUDFOUNDRY_PLATFORM_TYPE) || runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"),
            "stream-app-cross-version-test: SKIP - CloudFoundry 2.6 and below!");

        Assumptions.assumeTrue(runtimeApps.isAppRegistered("ver-log", ApplicationType.sink, VERSION_3_0_1) && runtimeApps.isAppRegistered("ver-log",
            ApplicationType.sink,
            VERSION_2_1_5), "stream-app-cross-version-test: SKIP - required ver-log apps not registered!");

        logger.info("stream-app-cross-version-test: DEPLOY");

        int CURRENT_MANIFEST = 0;
        String RANDOM_SUFFIX = randomSuffix();

        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("app-cross-version-test" + RANDOM_SUFFIX)
            .definition("http | ver-log")
            .create()
            .deploy(new DeploymentPropertiesBuilder().putAll(testDeploymentProperties("http")).put("version.ver-log", VERSION_3_0_1).build())) {
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            // Helper supplier to retrieve the ver-log version from the stream's current manifest.
            Supplier<String> currentVerLogVersion = () -> new SpringCloudDeployerApplicationManifestReader().read(stream.manifest(CURRENT_MANIFEST))
                .stream()
                .filter(m -> m.getMetadata().get("name").equals("ver-log"))
                .map(m -> m.getSpec().getVersion())
                .findFirst()
                .orElse("none");
            runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);
            final String message1 = String.format("TEST MESSAGE 1-%s ", RANDOM_SUFFIX);
            runtimeApps.httpPost(stream.getName(), "http", message1);

            awaitValueInLog(stream, app("ver-log"), message1);

            assertThat(currentVerLogVersion.get()).isEqualTo(VERSION_3_0_1);
            assertThat(stream.history().size()).isEqualTo(1L);

            // UPDATE
            logger.info("stream-app-cross-version-test: UPDATE");

            stream.update(new DeploymentPropertiesBuilder().put("version.ver-log", VERSION_2_1_5).build());
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);
            final String message2 = String.format("TEST MESSAGE 2-%s ", RANDOM_SUFFIX);
            runtimeApps.httpPost(stream.getName(), "http", message2);

            awaitValueInLog(stream, app("ver-log"), message2);

            assertThat(currentVerLogVersion.get()).isEqualTo(VERSION_2_1_5);
            assertThat(stream.history().size()).isEqualTo(2);

            // ROLLBACK
            logger.info("stream-app-cross-version-test: ROLLBACK");

            stream.rollback(0);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);
            final String message3 = String.format("TEST MESSAGE 3-%s ", RANDOM_SUFFIX);
            runtimeApps.httpPost(stream.getName(), "http", message3);
            awaitValueInLog(stream, app("ver-log"), message3);

            assertThat(currentVerLogVersion.get()).isEqualTo(VERSION_3_0_1);
            assertThat(stream.history().size()).isEqualTo(3);
            logger.info("stream-app-cross-version-test: UNDEPLOY");
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }

        // DESTROY
        logger.info("stream-app-cross-version-test: DESTROY");

        assertThat(Optional.ofNullable(dataFlowOperations.streamOperations().list().getMetadata())
            .orElse(new PagedModel.PageMetadata(0, 0, 0))
            .getTotalElements()).isEqualTo(0L);
        logger.info("stream-app-cross-version:end");
    }

    @Test
    @Tag("group2")
    @Tag("smoke")
    public void streamLifecycle() {
        // Skip for SCDF 2.10.x on CloudFoundry due to '500 No Body' response
        if (runtimeApps.dataflowServerVersionLowerThan("2.11.0") &&
                runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.CLOUDFOUNDRY_PLATFORM_TYPE)) {
            logger.warn("Skipping streamLifecycle() test due to: 'SCDF 2.10.x on CloudFoundry w/ 500 [No Body] response'");
            return;
        }
        logger.info("stream-lifecycle:start");
        streamLifecycleHelper(1, s -> { });
        logger.info("stream-lifecycle:end");
    }

    @Test
    @Tag("group1")
    public void streamLifecycleWithTwoInstance() {
        logger.info("stream-lifecycle-with-two-instances:start");
        final int numberOfInstancePerApp = 2;
        streamLifecycleHelper(numberOfInstancePerApp, stream -> {
            Map<StreamApplication, Map<String, String>> streamApps = stream.runtimeApps();
            assertThat(streamApps.size()).isEqualTo(2);
            for (Map<String, String> instanceMap : streamApps.values()) {
                assertThat(instanceMap.size()).isEqualTo(numberOfInstancePerApp); // every app should have 2 instances.
            }
        });
        logger.info("stream-lifecycle-with-two-instances:end");
    }

    private void awaitValueInLog(Stream stream, final StreamApplication app, final String value) {
        AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream, app.getName());
        Awaitility.await("Value " + value + " in log " + app.getName())
            .failFast(() -> AwaitUtils.hasErrorInLog(offset))
            .conditionEvaluationListener(condition -> {
                if (condition.getRemainingTimeInMS() <= condition.getPollInterval().toMillis()) {
                    sendLogsToLogger("awaitValueInLog:" + value + ":failing", stream.logs(app));
                }
            })
            .until(() -> AwaitUtils.hasInLog(offset, value));
    }
    @SuppressWarnings("unchecked")
    private void sendLogsToLogger(String prefix, String logs) {
        ObjectMapper mapper = new ObjectMapper();
        if(!logs.startsWith("{")) {
            logger.info("{}:log:{}", prefix, logs);
            logger.info("{}:log:end", prefix);
        } else {
            Map<String, Object> logValues;
            try {
                logValues = mapper.readValue(logs, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                logger.warn(prefix + ":exception reading logs:" + logs + ":" + e, e);
                return;
            }
            if (logValues == null || logValues.isEmpty()) {
                logger.warn("{}:no logs from:{}", prefix, logs);
            } else {
                Map<String, Object> logMap = (Map<String, Object>) logValues.get("logs");
                if (logMap == null || logMap.isEmpty()) {
                    logger.warn("{}:cannot find logs in:{}", prefix, logs);
                } else {
                    for (Map.Entry<String, Object> entry : logMap.entrySet()) {
                        logger.info("{}:log for {}={}", prefix, entry.getKey(), entry.getValue());
                        logger.info("{}:log:end", prefix);
                    }
                }
            }
        }
    }
    private void awaitStarting(Stream stream, AwaitUtils.StreamLog offset) {
        final long startErrorCheck = System.currentTimeMillis() + 30_000L;
        Awaitility.await("Deployment starting for stream " + stream.getName())
            .failFast(() -> System.currentTimeMillis() > startErrorCheck && AwaitUtils.hasErrorInLog(offset))
            .conditionEvaluationListener(condition -> {
                if (condition.getRemainingTimeInMS() <= condition.getPollInterval().toMillis()) {
                    sendLogsToLogger("awaitStarting:failing", AwaitUtils.logOffset(stream).logs());
                }
            })
            .until(() -> {
                logger.debug("awaitStarting:{}:{}", stream.getName(), stream.getStatus());
                try {
                    return starting.contains(stream.getStatus());
                } catch (Throwable x) {
                    logger.debug("awaitStarting:ignoring:" + x);
                    return false;
                }
            });
    }

    private void awaitDeployed(Stream stream, AwaitUtils.StreamLog offset) {
        final long startErrorCheck = System.currentTimeMillis() + 15_000L;
        Awaitility.await("Deployment DEPLOYED for stream " + stream.getName())
            .timeout(Duration.ofMinutes(15))
            .failFast(() -> System.currentTimeMillis() >= startErrorCheck && AwaitUtils.hasErrorInLog(offset))
            .conditionEvaluationListener(condition -> {
                if (condition.getRemainingTimeInMS() <= condition.getPollInterval().toMillis()) {
                    sendLogsToLogger("awaitDeployed:failing", AwaitUtils.logOffset(stream).logs());
                }
            })
            .until(() -> {
                try {
                    String streamStatus = stream.getStatus();
                    logger.debug("awaitDeployed:status:{}={}", stream.getName(), streamStatus);
                    Collection<Map<String, String>> values = stream.runtimeApps().values();
                    logger.debug("awaitDeployed:deployed:{}={}", stream.getName(), values);
                    if(!streamStatus.equals(DEPLOYED)) {
                        return false;
                    }
                    return values.stream()
                            .allMatch(instanceState -> instanceState.values()
                                .stream().allMatch(state -> state.equals(DEPLOYED))
                            );
                } catch (Throwable x) {
                    if (System.currentTimeMillis() > startErrorCheck) {
                        throw x;
                    }
                    return false;
                }
            });
    }


    private void streamLifecycleHelper(int appInstanceCount, Consumer<Stream> streamAssertions) {
        logger.info("stream-lifecycle-test: DEPLOY");
        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("lifecycle-test" + randomSuffix())
            .definition("time | log --log.name='TEST' --log.expression='TICKTOCK - TIMESTAMP: '.concat(payload)")
            .create()
            .deploy(new DeploymentPropertiesBuilder().putAll(testDeploymentProperties("log"))
                .put("deployer.*.count", Integer.toString(appInstanceCount))
                .build())) {
            logger.info("stream-lifecycle-test: await deployment");
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            logger.info("stream-lifecycle-test:deployed");
            streamAssertions.accept(stream);

            Awaitility.await("log app has TICKTOCK - TIMESTAMP: in log")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("log")).contains("TICKTOCK - TIMESTAMP:"));

            assertThat(stream.history().size()).isEqualTo(1L);
            Awaitility.await("stream history[1] DEPLOYED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(1).equals(DEPLOYED));

            // UPDATE
            logger.info("stream-lifecycle-test: UPDATE");
            stream.update(new DeploymentPropertiesBuilder().put("app.log.log.expression", "'Updated TICKTOCK - TIMESTAMP: '.concat(payload)")
                .put("app.*.management.endpoints.web.exposure.include", "*")
                .build());
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            streamAssertions.accept(stream);

            Awaitility.await("log app has Updated TICKTOCK - TIMESTAMP: in log")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("log")).contains("Updated TICKTOCK - TIMESTAMP:"));

            assertThat(stream.history().size()).isEqualTo(2);
            Awaitility.await("stream history[1] DELETED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(1).equals(DELETED));
            Awaitility.await("stream history[2] DEPLOYED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(2).equals(DEPLOYED));

            // ROLLBACK
            logger.info("stream-lifecycle-test: ROLLBACK");
            stream.rollback(0);
            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);

            streamAssertions.accept(stream);

            Awaitility.await("log app has TICKTOCK - TIMESTAMP: in log")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.logs(app("log")).contains("TICKTOCK - TIMESTAMP:"));

            assertThat(stream.history().size()).isEqualTo(3);
            Awaitility.await("stream history[1] DELETED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(1).equals(DELETED));
            Awaitility.await("stream history[2] DELETED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(2).equals(DELETED));
            Awaitility.await("stream history[3] " + starting)
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .until(() -> starting.contains(stream.history().get(3)));
            Awaitility.await("stream history[3] DEPLOYED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(3).equals(DEPLOYED));

            // UNDEPLOY
            logger.info("stream-lifecycle-test: UNDEPLOY");
            stream.undeploy();
            Awaitility.await("stream status UNDEPLOYED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.getStatus().equals(UNDEPLOYED));

            assertThat(stream.history().size()).isEqualTo(3);
            Awaitility.await("stream history[1] DELETED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(1).equals(DELETED));
            Awaitility.await("stream history[2] DELETED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(2).equals(DELETED));
            Awaitility.await("stream history[3] DELETED")
                .timeout(Duration.ofMinutes(15))
                .pollInterval(20L, TimeUnit.SECONDS)
                .failFast(() -> AwaitUtils.hasErrorInLog(offset))
                .until(() -> stream.history().get(3).equals(DELETED));

            PagedModel<StreamDefinitionResource> list = dataFlowOperations.streamOperations().list();
            System.out.println("definitions:" + list.getContent());
            PagedModel.PageMetadata metadata = list.getMetadata();
            assertThat(metadata).isNotNull();
            assertThat(metadata.getTotalElements()).isEqualTo(1L);
            // DESTROY
        }
        logger.info("stream-lifecycle-test: DESTROY");
        PagedModel.PageMetadata metadata = dataFlowOperations.streamOperations().list().getMetadata();
        assertThat(metadata).isNotNull();
        assertThat(metadata.getTotalElements()).isEqualTo(0L);
    }

    @Test
    @Tag("group2")
    public void streamScaling() {
        logger.info("stream-scaling-test:start");
        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("stream-scaling-test" + randomSuffix())
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
            Awaitility.await("stream has 2 log apps")
                .timeout(Duration.ofMinutes(15))
                .until(() -> stream.runtimeApps().get(log).size() == 2);

            assertThat(stream.getStatus()).isEqualTo(DEPLOYED);
            streamApps = stream.runtimeApps();
            assertThat(streamApps.size()).isEqualTo(2);
            assertThat(streamApps.get(time).size()).isEqualTo(1);
            assertThat(streamApps.get(log).size()).isEqualTo(2);
        }
        logger.info("stream-scaling-test:end");
    }

    @Test
    @Tag("group6")
    public void namedChannelDestination() {
        logger.info("stream-named-channel-destination-test:start");
        String RANDON_SUFFIX = randomSuffix();
        String namedChannel = "LOG-DESTINATION" + RANDON_SUFFIX;
        try (Stream httpStream = Stream.builder(dataFlowOperations)
            .name("http-destination-source" + RANDON_SUFFIX)
            .definition("http > :" + namedChannel)
            .create()
            .deploy(testDeploymentProperties("http"));

             Stream logStream = Stream.builder(dataFlowOperations)
                 .name("log-destination-sink" + RANDON_SUFFIX)
                 .definition(":" + namedChannel + " > log")
                 .create()
                 .deploy(testDeploymentProperties("log"))
        ) {
            logger.info("namedChannelDestination:deploying:{}", logStream.getName());
            logger.info("namedChannelDestination:deploying:{}", httpStream.getName());
            AwaitUtils.StreamLog logOffset = AwaitUtils.logOffset(logStream);
            AwaitUtils.StreamLog httpOffset = AwaitUtils.logOffset(httpStream);
            awaitStarting(logStream, logOffset);
            awaitDeployed(logStream, logOffset);
            logger.info("namedChannelDestination:deployed:{}", logStream.getName());
            awaitDeployed(httpStream, httpOffset);
            logger.info("namedChannelDestination:deployed:{}", httpStream.getName());
            runtimeApps.httpPost(httpStream.getName(), "http", TESTING_ONE_TWO_THREE);
            String message = "Unique Test message: " + new Random().nextInt();
            logger.info("namedChannelDestination:sending:{} to {}", message, httpStream.getName());
            runtimeApps.httpPost(httpStream.getName(), "http", message);
            logger.info("namedChannelDestination:sent:{} to {}", message, httpStream.getName());
            Awaitility.await("log app has " + message + " in log")
                .timeout(Duration.ofMinutes(15))
                .failFast(() -> AwaitUtils.hasErrorInLog(httpOffset))
                .failFast(() -> AwaitUtils.hasErrorInLog(logOffset))
                .until(() -> logStream.logs(app("log")).contains(message));
            logger.info("namedChannelDestination:found:{} in {}", message, logStream.getName());
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("stream-named-channel-destination-test:end");
    }

    @Test
    @Tag("group2")
    public void namedChannelTap() {
        logger.info("named-channel-tap:start");
        String RANDOM_SUFFIX = randomSuffix();
        String namedChannel = "taphttp" + RANDOM_SUFFIX;
        try (Stream httpLogStream = Stream.builder(dataFlowOperations)
            .name(namedChannel)
            .definition("http | log")
            .create()
            .deploy(testDeploymentProperties("http"));

             Stream tapStream = Stream.builder(dataFlowOperations)
                 .name("tapstream" + RANDOM_SUFFIX)
                 .definition(":" + namedChannel + ".http > log")
                 .create()
                 .deploy(testDeploymentProperties("log"))
        ) {
            logger.info("namedChannelTap:deploying:{}", httpLogStream.getName());
            logger.info("namedChannelTap:deploying:{}", tapStream.getName());
            AwaitUtils.StreamLog httpOffset = AwaitUtils.logOffset(httpLogStream);
            AwaitUtils.StreamLog tapOffset = AwaitUtils.logOffset(tapStream);
            awaitStarting(httpLogStream, httpOffset);
            awaitDeployed(httpLogStream, httpOffset);
            logger.info("namedChannelTap:deployed:{}", httpLogStream.getName());
            awaitDeployed(tapStream, tapOffset);
            logger.info("namedChannelTap:deployed:{}", tapStream.getName());
            runtimeApps.httpPost(httpLogStream.getName(), "http", TESTING_ONE_TWO_THREE);
            String message = "Unique Test message: " + new Random().nextInt();
            logger.info("namedChannelTap:sending:{}:{}", httpLogStream.getName(), message);
            runtimeApps.httpPost(httpLogStream.getName(), "http", message);
            logger.info("namedChannelTap:sent:{}:{}", httpLogStream.getName(), message);
            Awaitility.await("log app has " + message + " in log")
                .timeout(Duration.ofMinutes(15))
                .failFast(() -> AwaitUtils.hasErrorInLog(tapOffset))
                .until(() -> tapStream.logs(app("log")).contains(message));
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("named-channel-tap:end");
    }

    @Test
    @Tag("group1")
    public void namedChannelManyToOne() {
        logger.info("named-channel-many-to-one:start");
        String RANDOM_SUFFIX = randomSuffix();
        String namedChannel = "MANY-TO-ONE-DESTINATION" + RANDOM_SUFFIX;
        try (Stream logStream = Stream.builder(dataFlowOperations)
            .name("many-to-one" + RANDOM_SUFFIX)
            .definition(":" + namedChannel + " > log")
            .create()
            .deploy(testDeploymentProperties("log"));
             Stream httpStreamOne = Stream.builder(dataFlowOperations)
                 .name("http-source-1" + RANDOM_SUFFIX)
                 .definition("http > :" + namedChannel)
                 .create()
                 .deploy(testDeploymentProperties("http"));
             Stream httpStreamTwo = Stream.builder(dataFlowOperations)
                 .name("http-source-2" + RANDOM_SUFFIX)
                 .definition("http > :" + namedChannel)
                 .create()
                 .deploy(testDeploymentProperties("http"))
        ) {
            AwaitUtils.StreamLog logOffset = AwaitUtils.logOffset(logStream);
            AwaitUtils.StreamLog httpOffsetOne = AwaitUtils.logOffset(httpStreamOne);
            AwaitUtils.StreamLog httpOffsetTwo = AwaitUtils.logOffset(httpStreamTwo);
            awaitStarting(logStream, logOffset);
            awaitDeployed(logStream, logOffset);

            awaitStarting(httpStreamOne, httpOffsetOne);
            awaitDeployed(httpStreamOne, httpOffsetOne);

            awaitStarting(httpStreamTwo, httpOffsetTwo);
            awaitDeployed(httpStreamTwo, httpOffsetTwo);

            runtimeApps.httpPost(httpStreamOne.getName(), "http", TESTING_ONE_TWO_THREE);

            final String messageOne = "Unique Test message: " + new Random().nextInt();
            runtimeApps.httpPost(httpStreamOne.getName(), "http", messageOne);

            awaitValueInLog(logStream, app("log"), messageOne);
            runtimeApps.httpPost(httpStreamTwo.getName(), "http", TESTING_ONE_TWO_THREE);
            final String messageTwo = "Unique Test message: " + new Random().nextInt();
            runtimeApps.httpPost(httpStreamTwo.getName(), "http", messageTwo);

            awaitValueInLog(logStream, app("log"), messageTwo);
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("named-channel-many-to-one:end");
    }

    @Test
    @Tag("group4")
    public void namedChannelDirectedGraph() {
        logger.info("named-channel-directed-graph:start");
        String RANDOM_SUFFIX = randomSuffix();
        String foo = "foo" + RANDOM_SUFFIX;
        String bar = "bar" + RANDOM_SUFFIX;
        try (
            Stream fooLogStream = Stream.builder(dataFlowOperations)
                .name("directed-graph-destination1" + RANDOM_SUFFIX)
                .definition(":" + foo + " > transform --spel.function.expression=payload+'-foo' | log")
                .create()
                .deploy(testDeploymentProperties("log"));
            Stream barLogStream = Stream.builder(dataFlowOperations)
                .name("directed-graph-destination2" + RANDOM_SUFFIX)
                .definition(":" + bar + " > transform --spel.function.expression=payload+'-bar' | log")
                .create()
                .deploy(testDeploymentProperties("log"));
            Stream httpStream = Stream.builder(dataFlowOperations)
                .name("directed-graph-http-source" + RANDOM_SUFFIX)
                .definition("http | router --router.expression=payload.contains('a')?'"+foo+"':'"+bar+"'")
                .create()
                .deploy(testDeploymentProperties("http"))
        ) {
            logger.info("namedChannelDirectedGraph:deploying:{}", httpStream.getName());
            logger.info("namedChannelDirectedGraph:deploying:{}", fooLogStream.getName());
            logger.info("namedChannelDirectedGraph:deploying:{}", barLogStream.getName());
            AwaitUtils.StreamLog fooOffset = AwaitUtils.logOffset(fooLogStream);
            AwaitUtils.StreamLog barOffset = AwaitUtils.logOffset(barLogStream);
            AwaitUtils.StreamLog httpOffset = AwaitUtils.logOffset(httpStream);

            awaitStarting(fooLogStream, fooOffset);
            awaitDeployed(fooLogStream, fooOffset);

            logger.info("namedChannelDirectedGraph:deployed:{}", fooLogStream.getName());

            awaitStarting(barLogStream, barOffset);
            awaitDeployed(barLogStream, barOffset);
            logger.info("namedChannelDirectedGraph:deployed:{}", barLogStream.getName());

            awaitStarting(httpStream, httpOffset);
            awaitDeployed(httpStream, httpOffset);
            logger.info("namedChannelDirectedGraph:deployed:{}", httpStream.getName());
            runtimeApps.httpPost(httpStream.getName(), "http", TESTING_ONE_TWO_THREE);
            runtimeApps.httpPost(httpStream.getName(), "http", "abcd");
            logger.info("namedChannelDirectedGraph:sent:abcd -> {}", httpStream.getName());
            runtimeApps.httpPost(httpStream.getName(), "http", "defg");
            logger.info("namedChannelDirectedGraph:sent:defg -> {}", httpStream.getName());
            awaitValueInLog(fooLogStream, app("log"), "abcd-foo");
            awaitValueInLog(barLogStream, app("log"), "defg-bar");
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("named-channel-directed-graph:end");
    }

    @Test
    @Tag("group5")
    @Tag("smoke")
    @DisabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry")
    public void dataflowTaskLauncherSink() throws JsonProcessingException {
        logger.info("dataflow-task-launcher-sink:start");
        if (this.runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.LOCAL_PLATFORM_TYPE)) {
            logger.warn("Skipping since it doesn't work local");
        } else {
            String dataflowTaskLauncherAppName = "dataflow-tasklauncher";

            String skipOnIncompatibleDataFlowVersion = dataflowTaskLauncherAppName + "-sink-test: SKIP - Dataflow version:" + runtimeApps.getDataflowServerVersion() + " is older than 2.9.0-SNAPSHOT!";
            if (!runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.9.0-SNAPSHOT")) {
                logger.warn(skipOnIncompatibleDataFlowVersion);
            }
            Assumptions.assumeTrue(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.9.0-SNAPSHOT"), skipOnIncompatibleDataFlowVersion);

            String skipOnMissingAppRegistration = dataflowTaskLauncherAppName + "-sink-test: SKIP - no " + dataflowTaskLauncherAppName + " app registered!";
            boolean isDataflowTaskLauncherAppRegistered = runtimeApps.isAppRegistered(dataflowTaskLauncherAppName, ApplicationType.sink);
            if (!isDataflowTaskLauncherAppRegistered) {
                logger.info(skipOnMissingAppRegistration);
            }
            Assumptions.assumeTrue(isDataflowTaskLauncherAppRegistered, skipOnMissingAppRegistration);

            DetailedAppRegistrationResource dataflowTaskLauncherRegistration = dataFlowOperations.appRegistryOperations()
                .info(dataflowTaskLauncherAppName, ApplicationType.sink, false);

            logger.info("{}-sink-test: {} [{}], DataFlow [{}]",
                dataflowTaskLauncherAppName,
                dataflowTaskLauncherAppName,
                dataflowTaskLauncherRegistration.getVersion(),
                runtimeApps.getDataflowServerVersion());

            String taskName = randomTaskName();
            try (
                Task task = Task.builder(dataFlowOperations)
                    .name(taskName)
                    .definition("testtimestamp")
                    .description("Test timestamp task")
                    .build()
            ) {
                logger.info("dataflowTaskLauncherSink:deploying:{}", dataflowTaskLauncherAppName);
                try (
                    Stream stream = Stream.builder(dataFlowOperations)
                        .name("tasklauncher-test")
                        .definition("http | " + dataflowTaskLauncherAppName + " --trigger.initialDelay=100 --trigger.maxPeriod=1000 " + "--spring.cloud.dataflow.client.serverUri=" + dataFlowClientProperties.getServerUri())
                        .create()
                        .deploy(testDeploymentProperties("http"))
                ) {
                    AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);

                    awaitStarting(stream, offset);
                    awaitDeployed(stream, offset);

                    Map<String, Object> data = new HashMap<>();
                    data.put("name", taskName);
                    runtimeApps.httpPostJson(stream.getName(), "http", data);
                    AtomicReference<LaunchResponseResource> launch = new AtomicReference<>();
                    Awaitility.await("task " + taskName + " status COMPLETE")
                        .until(() -> task.executions()
                            .stream()
                            .filter(t -> t.getTaskName().equals(taskName) && t.getTaskExecutionStatus() == TaskExecutionStatus.COMPLETE)
                            .findFirst()
                            .map(t -> {
                                launch.getAndSet(new LaunchResponseResource(t.getExecutionId(), t.getSchemaTarget()));
                                return t;
                            })
                            .isPresent());
                    assertThat(launch.get()).isNotNull();
                    assertThat(task.executions().size()).isEqualTo(1);
                    Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch.get().getExecutionId(), launch.get().getSchemaTarget());
                    assertThat(taskExecutionResource).isPresent();
                    assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
                }
            } catch (Throwable x) {
                if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                    throw x;
                } else {
                    logger.warn("Older version may fail with " + x);
                }
            }
        }
        logger.info("dataflow-task-launcher-sink:start");
    }

    // -----------------------------------------------------------------------
    // STREAM METRICS TESTS
    // -----------------------------------------------------------------------
    @Test
    @Tag("group4")
    public void analyticsCounterInflux() {
        logger.info("analytics-counter-influx:start");
        if (!influxPresent()) {
            logger.info("stream-analytics-test: SKIP - no InfluxDB metrics configured!");
            return;
        }

        Assumptions.assumeTrue(influxPresent());

        if (!runtimeApps.isAppRegistered("analytics", ApplicationType.sink)) {
            logger.info("stream-analytics-influx-test: SKIP - no analytics app registered!");
        }

        Assumptions.assumeTrue(runtimeApps.isAppRegistered("analytics", ApplicationType.sink), "stream-analytics-test: SKIP - no analytics app registered!");

        logger.info("stream-analytics-influx-test");

        try (
            Stream stream = Stream.builder(dataFlowOperations)
                .name("httpAnalyticsInflux")
                .definition("http | analytics --analytics.name=my_http_analytics --analytics.tag.expression.msgSize=payload.length()")
                .create()
                .deploy(testDeploymentProperties("http"))
        ) {
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);

            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);

            String message1 = "Test message 1"; // length 14
            String message2 = "Test message 2 with extension"; // length 29
            String message3 = "Test message 2 with double extension"; // length 36


            runtimeApps.httpPost(stream.getName(), "http", message1);
            runtimeApps.httpPost(stream.getName(), "http", message2);
            runtimeApps.httpPost(stream.getName(), "http", message3);

            // Wait for ~1 min for Micrometer to send first metrics to Influx.
            Awaitility.await("Wait for Micrometer to send metrics")
                .until(() -> !JsonPath.parse(runtimeApps.httpGet(testProperties.getPlatform()
                        .getConnection()
                        .getInfluxUrl() + "/query?db=myinfluxdb&q=SELECT * FROM \"my_http_analytics\""))
                    .read("$.results[0][?(@.series)].length()")
                    .toString()
                    .equals("[]"));

            // http://localhost:8086/query?db=myinfluxdb&q=SELECT%20%22count%22%20FROM%20%22spring_integration_send%22
            // http://localhost:8086/query?db=myinfluxdb&q=SHOW%20MEASUREMENTS

            // http://localhost:8086/query?db=myinfluxdb&q=SELECT%20value%20FROM%20%22message_my_http_counter%22%20GROUP%20BY%20%2A%20ORDER%20BY%20ASC%20LIMIT%201

            // http://localhost:8086/query?q=SHOW%20DATABASES
            JsonAssertions.assertThatJson(runtimeApps.httpGet(testProperties.getPlatform().getConnection().getInfluxUrl() + "/query?q=SHOW DATABASES"))
                .inPath("$.results[0].series[0].values[1][0]")
                .isEqualTo("myinfluxdb");

            List<String> messageLengths = java.util.stream.Stream.of(message1, message2, message3)
                .map(s -> String.format("\"%s\"", s.length()))
                .collect(Collectors.toList());

            // http://localhost:8086/query?db=myinfluxdb&q=SELECT%20%2A%20FROM%20%22my_http_counter%22
            String myHttpCounter = runtimeApps.httpGet(testProperties.getPlatform()
                .getConnection()
                .getInfluxUrl() + "/query?db=myinfluxdb&q=SELECT * FROM \"my_http_analytics\"");
            JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[0][7]").isIn(messageLengths);
            JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[1][7]").isIn(messageLengths);
            JsonAssertions.assertThatJson(myHttpCounter).inPath("$.results[0].series[0].values[2][7]").isIn(messageLengths);
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("analytics-counter-influx:end");
    }

    @Test
    @Tag("group5")
    public void analyticsCounterPrometheus() throws IOException {
        logger.info("analytics-counter-prometheus:start");
        if (!runtimeApps.isAppRegistered("analytics", ApplicationType.sink)) {
            logger.info("stream-analytics-prometheus-test: SKIP - no analytics app registered!");
            return;
        }

        Assumptions.assumeTrue(runtimeApps.isAppRegistered("analytics", ApplicationType.sink), "stream-analytics-test: SKIP - no analytics app registered!");

        if (!prometheusPresent()) {
            logger.info("stream-analytics-prometheus-test: SKIP - no Prometheus configured!");
        }
        Assumptions.assumeTrue(prometheusPresent());

        logger.info("stream-analytics-prometheus-test");

        try (
            Stream stream = Stream.builder(dataFlowOperations)
                .name("httpAnalyticsPrometheus")
                .definition("http | analytics --analytics.name=my_http_analytics --analytics.tag.expression.msgSize=payload.length()")
                .create()
                .deploy(testDeploymentProperties("http"))
        ) {
            AwaitUtils.StreamLog offset = AwaitUtils.logOffset(stream);

            awaitStarting(stream, offset);
            awaitDeployed(stream, offset);
            runtimeApps.httpPost(stream.getName(), "http", TESTING_ONE_TWO_THREE);

            String message1 = "Test message 1"; // length 14
            String message2 = "Test message 2 with extension"; // length 29
            String message3 = "Test message 2 with double extension"; // length 36

            runtimeApps.httpPost(stream.getName(), "http", message1);
            runtimeApps.httpPost(stream.getName(), "http", message2);
            runtimeApps.httpPost(stream.getName(), "http", message3);

            // Wait for ~1 min for Micrometer to send first metrics to Prometheus.
            Awaitility.await("Wait for Micrometers to send first metrics to Prometheus")
                .until(() -> (int) JsonPath.parse(runtimeApps.httpGet(testProperties.getPlatform()
                    .getConnection()
                    .getPrometheusUrl() + "/api/v1/query?query=my_http_analytics_total")).read("$.data.result.length()") > 0);

            JsonAssertions.assertThatJson(runtimeApps.httpGet(testProperties.getPlatform()
                .getConnection()
                .getPrometheusUrl() + "/api/v1/query?query=my_http_analytics_total")).isEqualTo(resourceToString("classpath:/my_http_analytics_total.json"));
        } catch (Throwable x) {
            if (runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.10.0-SNAPSHOT")) {
                throw x;
            } else {
                logger.warn("Older version may fail with " + x);
            }
        }
        logger.info("analytics-counter-prometheus:end");
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
        DeploymentPropertiesBuilder propertiesBuilder = new DeploymentPropertiesBuilder().put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME,
                runtimeApps.getPlatformName())
            .put("app.*.logging.file", "/tmp/${PID}-test.log") // Keep it for Boot 2.x compatibility.
            .put("app.*.logging.file.name", "/tmp/${PID}-test.log")
            .put("app.*.endpoints.logfile.sensitive", "false")
            .put("app.*.endpoints.logfile.enabled", "true")
            .put("app.*.management.endpoints.web.exposure.include", "*")
            .put("app.*.spring.cloud.streamapp.security.enabled", "false");


        if (this.runtimeApps.getPlatformType().equalsIgnoreCase(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            propertiesBuilder.put("app.*.server.port", "8080");
            if(externallyAccessibleApps != null) {
                for (String appName : externallyAccessibleApps) {
                    propertiesBuilder.put("deployer." + appName + ".kubernetes.createLoadBalancer", "true"); // requires LoadBalancer support on the platform
                }
            }
        }

        return propertiesBuilder.build();
    }

    protected Map<String, String> testDeploymentProperties(Map<String, String> deploymentProperties, String... externallyAccessibleApps) {
        DeploymentPropertiesBuilder propertiesBuilder = new DeploymentPropertiesBuilder().put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME,
                runtimeApps.getPlatformName())
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
        propertiesBuilder.putAll(deploymentProperties);
        return propertiesBuilder.build();
    }

    public static String resourceToString(String resourcePath) throws IOException {
        return StreamUtils.copyToString(new DefaultResourceLoader().getResource(resourcePath).getInputStream(), StandardCharsets.UTF_8);
    }

    protected boolean prometheusPresent() {
        return runtimeApps.isServicePresent(testProperties.getPlatform().getConnection().getPrometheusUrl() + "/api/v1/query?query=up");
    }

    protected boolean influxPresent() {
        return runtimeApps.isServicePresent(testProperties.getPlatform().getConnection().getInfluxUrl() + "/ping");
    }


    protected StreamApplication app(String appName) {
        return new StreamApplication(appName);
    }

    // -----------------------------------------------------------------------
    // TASK TESTS
    // -----------------------------------------------------------------------
    public static final int EXIT_CODE_SUCCESS = 0;

    public static final int EXIT_CODE_ERROR = 1;

    public static final String TEST_VERSION_NUMBER = "2.0.1";

    private List<String> composedTaskLaunchArguments(String... additionalArguments) {
        // the dataflow-server-use-user-access-token=true argument is required COMPOSED tasks in
        // oauth2-protected SCDF installations and is ignored otherwise.
        List<String> commonTaskArguments = new ArrayList<>();
        commonTaskArguments.add("--dataflow-server-use-user-access-token=true");
        commonTaskArguments.addAll(Arrays.asList(additionalArguments));
        return commonTaskArguments;
    }

    @Test
    @EnabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "local")
    @Tag("group6")
    public void runBatchRemotePartitionJobLocal() {
        logger.info("run-batch-remote-partition-job-local:start");
        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder.name(randomTaskName()).definition("batch-remote-partition").description("runBatchRemotePartitionJob - local").build()) {

            LaunchResponseResource launch = task.launch(Collections.emptyMap(), composedTaskLaunchArguments("--platform=local"));

            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(1);
            Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(taskExecutionResource).isPresent();
            assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
        }
        logger.info("run-batch-remote-partition-job-local:end");
    }

    @Test
    @Tag("group1")
    public void taskMetricsPrometheus() throws IOException {
        logger.info("task-metrics-prometheus:start");
        if (!prometheusPresent()) {
            logger.info("task-metrics-test: SKIP - no metrics configured!");
            return;
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
            LaunchResponseResource launch = task.launch(Collections.singletonList("--spring.cloud.task.closecontext_enabled=false"));

            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(1);
            Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(taskExecutionResource).isPresent();
            assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            // All
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            URI qplUri = UriComponentsBuilder.fromHttpUrl(testProperties.getPlatform().getConnection().getPrometheusUrl() + String.format(
                    "/api/v1/query?query=system_cpu_usage{service=\"task-application\",application=\"%s-%s\"}",
                    task.getTaskName(),
                    launch.getExecutionId()))
                .build()
                .toUri();

            Supplier<String> pqlTaskMetricsQuery = () -> dataFlowOperations.getRestTemplate()
                .exchange(qplUri, HttpMethod.GET, null, String.class)
                .getBody();

            // Wait for ~1 min for Micrometer to send first metrics to Prometheus.
            Awaitility.await("Micrometer to send metrics to prometheus")
                .until(() -> (int) JsonPath.parse(pqlTaskMetricsQuery.get()).read("$.data.result.length()") > 0);

            JsonAssertions.assertThatJson(pqlTaskMetricsQuery.get()).isEqualTo(resourceToString("classpath:/task_metrics_system_cpu_usage.json"));
        }
        logger.info("task-metrics-prometheus:end");
    }

    @Test
    @Tag("group5")
    public void composedTask() {
        logger.info("task-composed-task-runner:start");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);

        try (Task task = taskBuilder.name(randomTaskName()).definition("a: testtimestamp && b: testtimestamp").description("Test composedTask").build()) {

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);

            // first launch
            LaunchResponseResource launch1 = task.launch(composedTaskLaunchArguments());

            validateSuccessfulTaskLaunch(task, launch1.getExecutionId(), launch1.getSchemaTarget());

            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                Optional<TaskExecutionResource> taskExecutionResource = childTask.executionByParentExecutionId(launch1.getExecutionId(),
                    launch1.getSchemaTarget());
                assertThat(taskExecutionResource).isPresent();
                assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });

            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // second launch
            LaunchResponseResource launch2 = task.launch(composedTaskLaunchArguments());

            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(launch2.getExecutionId(), launch2.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.executionStatus(launch2.getExecutionId(), launch2.getSchemaTarget())).isEqualTo(TaskExecutionStatus.COMPLETE);
            Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch2.getExecutionId(), launch2.getSchemaTarget());
            assertThat(taskExecutionResource).isPresent();
            assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(2);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch2.getExecutionId(), launch2.getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });

            assertThat(taskBuilder.allTasks().size()).isEqualTo(3);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
        logger.info("task-composed-task-runner:end");
    }

    @Test
    @Tag("group4")
    public void multipleComposedTaskWithArguments() {
        logger.info("task-multiple-composed-task-with-arguments:start");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder.name(randomTaskName())
            .definition("a: testtimestamp && b: testtimestamp")
            .description("Test multipleComposedTaskWithArguments")
            .build()) {

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);

            // first launch
            LaunchResponseResource launch1 = task.launch(composedTaskLaunchArguments("--increment-instance-enabled=true"));

            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(launch1.getExecutionId(), launch1.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.executionStatus(launch1.getExecutionId(), launch1.getSchemaTarget())).isEqualTo(TaskExecutionStatus.COMPLETE);
            Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch1.getExecutionId(), launch1.getSchemaTarget());
            assertThat(taskExecutionResource).isPresent();
            assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch1.getExecutionId(), launch1.getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });

            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // second launch
            LaunchResponseResource launch2 = task.launch(composedTaskLaunchArguments("--increment-instance-enabled=true"));

            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(launch2.getExecutionId(), launch2.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.executionStatus(launch2.getExecutionId(), launch2.getSchemaTarget())).isEqualTo(TaskExecutionStatus.COMPLETE);
            Optional<TaskExecutionResource> executionResource = task.execution(launch2.getExecutionId(), launch2.getSchemaTarget());
            assertThat(executionResource).isPresent();
            assertThat(executionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(2);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch2.getExecutionId(),
                    launch2.getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });

            assertThat(task.jobExecutionResources().size()).isEqualTo(2);

            assertThat(taskBuilder.allTasks().size()).isEqualTo(3);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
        logger.info("task-multiple-composed-task-with-arguments:end");
    }

    @Test
    @Tag("group4")
    public void multipleComposedTaskWithArgumentsBoot3() {
        logger.info("task-multiple-composed-task-with-arguments3:start");
        if (supportBoot3Jobs()) {
            TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
            try (Task task = taskBuilder.name(randomTaskName())
                .definition("a: testtimestamp3 && b: testtimestamp")
                .description("Test multipleComposedTaskWithArguments")
                .build()) {

                assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);

                // first launch
                LaunchResponseResource launch1 = task.launch(composedTaskLaunchArguments("--increment-instance-enabled=true"));

                Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                    .until(() -> task.executionStatus(launch1.getExecutionId(), launch1.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

                assertThat(task.executions().size()).isEqualTo(1);
                assertThat(task.executionStatus(launch1.getExecutionId(), launch1.getSchemaTarget())).isEqualTo(TaskExecutionStatus.COMPLETE);
                Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch1.getExecutionId(), launch1.getSchemaTarget());
                assertThat(taskExecutionResource).isPresent();
                assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

                task.composedTaskChildTasks().forEach(childTask -> {
                    assertThat(childTask.executions().size()).isEqualTo(1);
                    Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch1.getExecutionId(), launch1.getSchemaTarget());
                    assertThat(child).isPresent();
                    assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
                });

                task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

                // second launch
                LaunchResponseResource launch2 = task.launch(composedTaskLaunchArguments("--increment-instance-enabled=true"));

                Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                    .until(() -> task.executionStatus(launch2.getExecutionId(), launch2.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

                assertThat(task.executions().size()).isEqualTo(2);
                assertThat(task.executionStatus(launch2.getExecutionId(), launch2.getSchemaTarget())).isEqualTo(TaskExecutionStatus.COMPLETE);
                Optional<TaskExecutionResource> executionResource = task.execution(launch2.getExecutionId(), launch2.getSchemaTarget());
                assertThat(executionResource).isPresent();
                assertThat(executionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

                task.composedTaskChildTasks().forEach(childTask -> {
                    assertThat(childTask.executions().size()).isEqualTo(2);
                    Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch2.getExecutionId(),
                        launch2.getSchemaTarget());
                    assertThat(child).isPresent();
                    assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
                });

                assertThat(task.jobExecutionResources().size()).isEqualTo(2);

                assertThat(taskBuilder.allTasks().size()).isEqualTo(3);
            }
            assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
            logger.info("task-multiple-composed-task-with-arguments3:end");
        } else {
            logger.warn("task-multiple-composed-task-with-arguments:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group3")
    @Tag("smoke")
    public void ctrLaunchTest() {
        logger.info("ctr-launch:start");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder.name(randomTaskName()).definition("a: testtimestamp && b: testtimestamp").description("ctrLaunchTest").build()) {

            assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList())).hasSameElementsAs(fullTaskNames(task,
                "a",
                "b"));

            LaunchResponseResource launch = task.launch(composedTaskLaunchArguments());

            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

            // Parent Task Successfully completed
            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget())).isEqualTo(TaskExecutionStatus.COMPLETE);
            Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(taskExecutionResource).isPresent();
            assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // Child tasks successfully completed
            task.composedTaskChildTasks().forEach(childTask -> {
                assertThat(childTask).isNotNull();
                assertThat(childTask.executions().size()).isEqualTo(1);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch.getExecutionId(), launch.getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });

            // Attempt a job restart
            assertThat(task.executions().size()).isEqualTo(1);
            List<TaskExecutionResource> executions = new ArrayList<>(task.executions());
            assertThat(executions.size()).isEqualTo(1);
            final TaskExecutionResource executionResource = executions.get(0);
            assertThat(executionResource).isNotNull();
            assertThat(executionResource.getJobExecutionIds().size()).isEqualTo(1);

            // There is an Error deserialization issue related to backward compatibility with SCDF
            // 2.6.x
            // The Exception thrown by the 2.6.x servers can not be deserialized by the
            // VndErrorResponseErrorHandler in 2.8+ clients.
            Assumptions.assumingThat(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"), () ->
                assertThatThrownBy(() ->
                    dataFlowOperations.jobOperations().executionRestart(executionResource.getJobExecutionIds().get(0), executionResource.getSchemaTarget())
                ).isInstanceOf(DataFlowClientException.class)
                    .hasMessageContaining(" and state 'COMPLETED' is not restartable"));
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
        logger.info("ctr-launch:end");
    }

    @Test
    @Tag("group3")
    @Tag("smoke")
    public void ctrLaunchTestBoot3() {
        logger.info("ctr-launch3:start");
        if (supportBoot3Jobs()) {
            TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
            try (Task task = taskBuilder.name(randomTaskName()).definition("a: testtimestamp3 && b: testtimestamp").description("ctrLaunchTest").build()) {

                assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList())).hasSameElementsAs(fullTaskNames(task,
                    "a",
                    "b"));

                LaunchResponseResource launch = task.launch(composedTaskLaunchArguments());

                Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                    .atMost(Duration.ofMinutes(20))
                    .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

                // Parent Task Successfully completed
                assertThat(task.executions().size()).isEqualTo(1);
                assertThat(task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget())).isEqualTo(TaskExecutionStatus.COMPLETE);
                Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
                assertThat(taskExecutionResource).isPresent();
                assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
                task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

                // Child tasks successfully completed
                task.composedTaskChildTasks().forEach(childTask -> {
                    assertThat(childTask.executions().size()).isEqualTo(1);
                    Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch.getExecutionId(),
                        launch.getSchemaTarget());
                    assertThat(child).isPresent();
                    assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
                });

                // Attempt a job restart
                assertThat(task.executions().size()).isEqualTo(1);
                List<TaskExecutionResource> executions = new ArrayList<>(task.executions());
                assertThat(executions.size()).isEqualTo(1);
                final TaskExecutionResource executionResource = executions.get(0);
                assertThat(executionResource).isNotNull();
                assertThat(executionResource.getJobExecutionIds().size()).isEqualTo(1);

                // There is an Error deserialization issue related to backward compatibility with SCDF
                // 2.6.x
                // The Exception thrown by the 2.6.x servers can not be deserialized by the
                // VndErrorResponseErrorHandler in 2.8+ clients.
                Assumptions.assumingThat(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"), () ->
                    assertThatThrownBy(() ->
                        dataFlowOperations.jobOperations().executionRestart(executionResource.getJobExecutionIds().get(0), executionResource.getSchemaTarget())
                    ).isInstanceOf(DataFlowClientException.class)
                        .hasMessageContaining(" and state 'COMPLETED' is not restartable"));
            }
            assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
            logger.info("ctr-launch:end");
        } else {
            logger.info("ctr-launch:skip for " + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group4")
    public void ctrFailedGraph() {
        logger.info("ctr-failed-graph:start");
        mixedSuccessfulFailedAndUnknownExecutions("ctrFailedGraph",
            "scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false && testtimestamp",
            TaskExecutionStatus.ERROR,
            emptyList(),
            // successful
            asList("scenario"),
            // failed
            asList("testtimestamp"));
        logger.info("ctr-failed-graph:end");// not-run
    }

    @Test
    @Tag("group4")
    public void ctrFailedGraphBoot3() {
        logger.info("ctr-failed-graph3:start");
        if (supportBoot3Jobs()) {
            mixedSuccessfulFailedAndUnknownExecutions("ctrFailedGraph",
                "scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false && testtimestamp3",
                TaskExecutionStatus.ERROR,
                emptyList(),
                // successful
                asList("scenario"),
                // failed
                asList("testtimestamp3"));
            logger.info("ctr-failed-graph:end");// not-run
        } else {
            logger.warn("ctr-failed-graph3:skipped for " + runtimeApps.getDataflowServerVersion());// not-run
        }

    }

    @Test
    @Tag("group1")
    public void ctrSplit() {
        logger.info("ctr-split:start");
        allSuccessfulExecutions("ComposedTask Split Test", "<t1:timestamp || t2:timestamp || t3:timestamp>", "t1", "t2", "t3");
        logger.info("ctr-split:end");
    }

    @Test
    @Tag("group1")
    public void ctrSplitBoot3() {
        logger.info("ctr-split3:start");
        if (supportBoot3Jobs()) {
            allSuccessfulExecutions("ComposedTask Split Test", "<t1:testtimestamp3 || t2:timestamp || t3:testtimestamp3>", "t1", "t2", "t3");
            logger.info("ctr-split3:end");
        } else {
            logger.info("ctr-split3:skipped for " + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group1")
    public void ctrSequential() {
        logger.info("ctr-sequential:start");
        allSuccessfulExecutions("ComposedTask Sequential Test", "t1: testtimestamp && t2: testtimestamp && t3: testtimestamp", "t1", "t2", "t3");
        logger.info("ctr-sequential:end");
    }

    @Test
    @Tag("group1")
    public void ctrSequentialBoot3() {
        logger.info("ctr-sequential3:start");
        if (supportBoot3Jobs()) {
            allSuccessfulExecutions("ComposedTask Sequential Test", "t1: testtimestamp3 && t2: testtimestamp && t3: testtimestamp3", "t1", "t2", "t3");
            logger.info("ctr-sequential3:end");
        } else {
            logger.warn("ctr-sequential3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group5")
    public void ctrSequentialTransitionAndSplitWithScenarioFailed() {
        logger.info("ctr-sequential-transition-and-split-withScenario-failed:start");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split With Scenario Failed Test",
            "t1: testtimestamp && scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED'->t3: testtimestamp && <t4: testtimestamp || t5: testtimestamp> && t6: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("t1", "t3"),
            // successful
            asList("scenario"),
            // failed
            asList("t4", "t5", "t6")); // not-run
        logger.info("ctr-sequential-transition-and-split-withScenario-failed:end");
    }

    @Test
    @Tag("group5")
    public void ctrSequentialTransitionAndSplitWithScenarioFailedBoot3() {
        logger.info("ctr-sequential-transition-and-split-withScenario-failed3:start");
        if (supportBoot3Jobs()) {
            mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split With Scenario Failed Test",
                "t1: testtimestamp3 && scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED'->t3: testtimestamp3 && <t4: testtimestamp || t5: testtimestamp3> && t6: testtimestamp3",
                TaskExecutionStatus.COMPLETE,
                asList("t1", "t3"),
                // successful
                asList("scenario"),
                // failed
                asList("t4", "t5", "t6")); // not-run
            logger.info("ctr-sequential-transition-and-split-withScenario-failed3:end");
        } else {
            logger.warn("ctr-sequential-transition-and-split-withScenario-failed3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group5")
    public void ctrSequentialTransitionAndSplitWithScenarioOk() {
        logger.info("ctr-sequential-transition-and-split-with-scenario-ok:start");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split With Scenario Ok Test",
            "t1: testtimestamp && t2: scenario 'FAILED'->t3: testtimestamp && <t4: testtimestamp || t5: testtimestamp> && t6: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("t1", "t2", "t4", "t5", "t6"),
            // successful
            emptyList(),
            // failed
            asList("t3")); // not-run
        logger.info("ctr-sequential-transition-and-split-with-scenario-ok:end");
    }

    @Test
    @Tag("group5")
    public void ctrSequentialTransitionAndSplitWithScenarioOkBoot3() {
        logger.info("ctr-sequential-transition-and-split-with-scenario-ok3:start");
        if (supportBoot3Jobs()) {
            mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split With Scenario Ok Test",
                "t1: testtimestamp3 && t2: scenario 'FAILED'->t3: testtimestamp && <t4: testtimestamp3 || t5: testtimestamp> && t6: testtimestamp3",
                TaskExecutionStatus.COMPLETE,
                asList("t1", "t2", "t4", "t5", "t6"),
                // successful
                emptyList(),
                // failed
                asList("t3")); // not-run
            logger.info("ctr-sequential-transition-and-split-with-scenario-ok3:end");
        } else {
            logger.warn("ctr-sequential-transition-and-split-with-scenario-ok3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group1")
    public void ctrNestedSplit() {
        logger.info("composed-task-NestedSplit");
        allSuccessfulExecutions("ctrNestedSplit",
            "<<t1: testtimestamp || t2: testtimestamp > && t3: testtimestamp || t4: testtimestamp>",
            "t1",
            "t2",
            "t3",
            "t4");
    }

    @Test
    @Tag("group1")
    public void ctrNestedSplitBoot3() {
        logger.info("composed-task-NestedSplit3");
        if (supportBoot3Jobs()) {
            allSuccessfulExecutions("ctrNestedSplit",
                "<<t1: testtimestamp || t2: testtimestamp3 > && t3: testtimestamp || t4: testtimestamp3>",
                "t1",
                "t2",
                "t3",
                "t4");
        } else {
            logger.warn("composed-task-NestedSplit3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group5")
    public void testEmbeddedFailedGraph() {
        logger.info("composed-task-EmbeddedFailedGraph-test");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Embedded Failed Graph Test",
            String.format(
                "a: testtimestamp && b:scenario  --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true && c: testtimestamp",
                randomJobName()),
            TaskExecutionStatus.ERROR,
            asList("a"),
            // successful
            asList("b"),
            // failed
            asList("c")); // not-run
    }

    @Test
    @Tag("group5")
    public void twoSplitTest() {
        logger.info("composed-task-twoSplit-test");
        allSuccessfulExecutions("twoSplitTest",
            "<t1: testtimestamp ||t2: testtimestamp||t3: testtimestamp> && <t4: testtimestamp||t5: testtimestamp>",
            "t1",
            "t2",
            "t3",
            "t4",
            "t5");
    }

    @Test
    @Tag("group5")
    public void twoSplitTestBoot3() {
        logger.info("composed-task-twoSplit-test3");
        if (supportBoot3Jobs()) {
            allSuccessfulExecutions("twoSplitTest",
                "<t1: testtimestamp3 ||t2: testtimestamp||t3: testtimestamp3> && <t4: testtimestamp||t5: testtimestamp3>",
                "t1",
                "t2",
                "t3",
                "t4",
                "t5");
        } else {
            logger.warn("composed-task-twoSplit-test3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group4")
    public void sequentialAndSplitTest() {
        logger.info("composed-task-sequentialAndSplit-test");
        allSuccessfulExecutions("sequentialAndSplitTest",
            "<t1: testtimestamp && <t2: testtimestamp || t3: testtimestamp || t4: testtimestamp> && t5: testtimestamp>",
            "t1",
            "t2",
            "t3",
            "t4",
            "t5");
    }

    @Test
    @Tag("group4")
    public void sequentialAndSplitTestBoot3() {
        logger.info("composed-task-sequentialAndSplit-test3");
        if (supportBoot3Jobs()) {
            allSuccessfulExecutions("sequentialAndSplitTest",
                "<t1: testtimestamp3 && <t2: testtimestamp || t3: testtimestamp3 || t4: testtimestamp> && t5: testtimestamp3>",
                "t1",
                "t2",
                "t3",
                "t4",
                "t5");
        } else {
            logger.warn("composed-task-sequentialAndSplit-test3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group6")
    public void sequentialTransitionAndSplitFailedInvalidTest() {
        logger.info("composed-task-sequentialTransitionAndSplitFailedInvalid-test");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split Failed Invalid Test",
            "t1: testtimestamp && b:scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t2: testtimestamp && t3: testtimestamp && t4: testtimestamp && <t5: testtimestamp || t6: testtimestamp> && t7: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("t1", "t2"),
            // successful
            asList("b"),
            // failed
            asList("t3", "t4", "t5", "t6", "t7")); // not-run
    }

    @Test
    @Tag("group6")
    public void sequentialTransitionAndSplitFailedInvalidTestBoot3() {
        logger.info("composed-task-sequentialTransitionAndSplitFailedInvalid-test3");
        if (supportBoot3Jobs()) {
            mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Transition And Split Failed Invalid Test",
                "t1: testtimestamp && b:scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t2: testtimestamp3 && t3: testtimestamp && t4: testtimestamp3 && <t5: testtimestamp || t6: testtimestamp> && t7: testtimestamp3",
                TaskExecutionStatus.COMPLETE,
                asList("t1", "t2"),
                // successful
                asList("b"),
                // failed
                asList("t3", "t4", "t5", "t6", "t7")); // not-run
        } else {
            logger.warn("composed-task-sequentialTransitionAndSplitFailedInvalid-test3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group3")
    public void sequentialAndSplitWithFlowTestBoot3() {
        logger.info("composed-task-sequentialAndSplitWithFlow-test3");
        if (supportBoot3Jobs()) {
            allSuccessfulExecutions("sequentialAndSplitWithFlowTest",
                "t1: testtimestamp && <t2: testtimestamp3 && t3: testtimestamp || t4: testtimestamp3 ||t5: testtimestamp> && t6: testtimestamp3",
                "t1",
                "t2",
                "t3",
                "t4",
                "t5",
                "t6");
        } else {
            logger.warn("composed-task-sequentialAndSplitWithFlow-test3:skipped" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group3")
    public void sequentialAndSplitWithFlowTest() {
        logger.info("composed-task-sequentialAndSplitWithFlow-test");
        allSuccessfulExecutions("sequentialAndSplitWithFlowTest",
            "t1: testtimestamp && <t2: testtimestamp && t3: testtimestamp || t4: testtimestamp ||t5: testtimestamp> && t6: testtimestamp",
            "t1",
            "t2",
            "t3",
            "t4",
            "t5",
            "t6");
    }

    @Test
    @Tag("group3")
    public void sequentialAndFailedSplitTest() {
        logger.info("composed-task-sequentialAndFailedSplit-test");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder.name(randomTaskName())
            .definition(String.format(
                "t1: testtimestamp && <t2: testtimestamp || b:scenario --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true || t3: testtimestamp> && t4: testtimestamp",
                randomJobName()))
            .description("sequentialAndFailedSplitTest")
            .build()) {

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(5);
            assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList())).hasSameElementsAs(fullTaskNames(task,
                "b",
                "t1",
                "t2",
                "t3",
                "t4"));

            LaunchResponseResource launch = task.launch(composedTaskLaunchArguments());

            if (runtimeApps.dataflowServerVersionLowerThan("2.8.0-SNAPSHOT")) {
                Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                    .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            } else {
                Awaitility.await("task " + task.getTaskName() + " is ERROR")
                    .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.ERROR);
            }

            // Parent Task
            assertThat(task.executions().size()).isEqualTo(1);
            Optional<TaskExecutionResource> resource = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(resource).isPresent();
            assertThat(resource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));

            // Successful
            childTasksBySuffix(task, "t1", "t2", "t3").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch.getExecutionId(), launch.getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });

            // Failed tasks
            childTasksBySuffix(task, "b").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch.getExecutionId(),
                    launch.getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isNotEqualTo(EXIT_CODE_SUCCESS);
            });

            // Not run tasks
            childTasksBySuffix(task, "t4").forEach(childTask ->
                assertThat(childTask.executions().size()).isEqualTo(0)
            );

            // Parent Task
            assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);

            // restart job
            assertThat(task.executions().size()).isEqualTo(1);
            Optional<TaskExecutionResource> taskExecutionResource = task.executions().stream().findFirst();
            assertThat(taskExecutionResource).isPresent();
            List<Long> jobExecutionIds = taskExecutionResource.get().getJobExecutionIds();
            assertThat(jobExecutionIds.size()).isEqualTo(1);
            dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0), launch.getSchemaTarget());

            Optional<TaskExecutionResource> taskExecutionResource2 = task.executions()
                .stream()
                .max(Comparator.comparingLong(TaskExecutionResource::getExecutionId));
            assertThat(taskExecutionResource2).isPresent();

            Awaitility.await("task " + taskExecutionResource2.get().getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(taskExecutionResource2.get().getExecutionId(),
                    taskExecutionResource2.get().getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(task.executionStatus(taskExecutionResource2.get().getExecutionId(), taskExecutionResource2.get().getSchemaTarget())).isEqualTo(
                TaskExecutionStatus.COMPLETE);
            Optional<TaskExecutionResource> executionResource = task.execution(taskExecutionResource2.get().getExecutionId(),
                taskExecutionResource2.get().getSchemaTarget());
            assertThat(executionResource).isPresent();
            assertThat(executionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

            childTasksBySuffix(task, "b").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(2);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(taskExecutionResource.get().getExecutionId(),
                    taskExecutionResource.get().getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isNotEqualTo(EXIT_CODE_SUCCESS);
            });

            childTasksBySuffix(task, "t4").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(taskExecutionResource2.get().getExecutionId(),
                    taskExecutionResource2.get().getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });

            assertThat(task.jobExecutionResources().size()).isEqualTo(2);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
    }

    @Test
    @Tag("group6")
    public void failedBasicTransitionTest() {
        logger.info("failed-basic-transition-test:start");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Failed Basic Transition Test",
            "b: scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp * ->t2: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("t1"),
            // successful
            asList("b"),
            // failed
            asList("t2")); // not-run
        logger.info("failed-basic-transition-test:end");
    }

    @Test
    @Tag("group6")
    public void failedBasicTransitionTestBoot3() {
        logger.info("failed-basic-transition-test3:start");
        if (supportBoot3Jobs()) {
            mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Sequential Failed Basic Transition Test",
                "b: scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp3 * ->t2: testtimestamp3",
                TaskExecutionStatus.COMPLETE,
                asList("t1"),
                // successful
                asList("b"),
                // failed
                asList("t2")); // not-run
            logger.info("failed-basic-transition-test3:end");
        } else {
            logger.warn("failed-basic-transition-test3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group2")
    public void successBasicTransitionTest() {
        logger.info("success-basic-transition-test:start");
        mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Success Basic Transition Test",
            "b: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp * ->t2: testtimestamp",
            TaskExecutionStatus.COMPLETE,
            asList("b", "t2"),
            // successful
            emptyList(),
            // failed
            asList("t1")); // not-run
        logger.info("success-basic-transition-test:end");
    }

    @Test
    @Tag("group2")
    public void successBasicTransitionTestBoot3() {
        logger.info("success-basic-transition-test3:start");
        if (supportBoot3Jobs()) {
            mixedSuccessfulFailedAndUnknownExecutions("ComposedTask Success Basic Transition Test",
                "b: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp3 * ->t2: testtimestamp",
                TaskExecutionStatus.COMPLETE,
                asList("b", "t2"),
                // successful
                emptyList(),
                // failed
                asList("t1")); // not-run
            logger.info("success-basic-transition-test3:end");
        } else {
            logger.warn("success-basic-transition-test3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group3")
    public void basicTransitionWithTransitionTest() {
        logger.info("composed-task-basicTransitionWithTransition-test");
        mixedSuccessfulFailedAndUnknownExecutions("basicTransitionWithTransitionTest",
            "b1: scenario  --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp  && b2: scenario --io.spring.launch-batch-job=false 'FAILED' -> t2: testtimestamp * ->t3: testtimestamp ",
            TaskExecutionStatus.COMPLETE,
            asList("b1", "b2", "t3"),
            // successful
            emptyList(),
            // failed
            asList("t1", "t2")); // not-run
    }

    @Test
    @Tag("group2")
    public void wildCardOnlyInLastPositionTest() {
        logger.info("composed-task-wildCardOnlyInLastPosition-test");
        mixedSuccessfulFailedAndUnknownExecutions("wildCardOnlyInLastPositionTest",
            "b1: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp  && b2: scenario --io.spring.launch-batch-job=false * ->t3: testtimestamp ",
            TaskExecutionStatus.COMPLETE,
            asList("b1", "b2", "t3"),
            // successful
            emptyList(),
            // failed
            asList("t1")); // not-run
    }

    @Test
    @Tag("group2")
    public void wildCardOnlyInLastPositionTestBoot3() {
        logger.info("composed-task-wildCardOnlyInLastPosition-test3");
        if (supportBoot3Jobs()) {
            mixedSuccessfulFailedAndUnknownExecutions("wildCardOnlyInLastPositionTest",
                "b1: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: testtimestamp3  && b2: scenario --io.spring.launch-batch-job=false * ->t3: testtimestamp3 ",
                TaskExecutionStatus.COMPLETE,
                asList("b1", "b2", "t3"),
                // successful
                emptyList(),
                // failed
                asList("t1")); // not-run
        } else {
            logger.warn("composed-task-wildCardOnlyInLastPosition-test3:skipped:" + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group6")
    public void failedCTRRetryTest() {
        logger.info("composed-task-failedCTRRetry-test");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder.name(randomTaskName())
            .definition(String.format(
                "b1:scenario --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true && t1: testtimestamp",
                randomJobName()))
            .description("failedCTRRetryTest")
            .build()) {
            logger.info("composed-task-failedCTRRetry-test:definition");
            assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);
            assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList())).hasSameElementsAs(fullTaskNames(task,
                "b1",
                "t1"));

            LaunchResponseResource launch = task.launch(composedTaskLaunchArguments());
            logger.info("composed-task-failedCTRRetry-test:launch");
            if (runtimeApps.dataflowServerVersionLowerThan("2.8.0-SNAPSHOT")) {
                Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                    .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            } else {
                Awaitility.await("task " + task.getTaskName() + " is ERROR")
                    .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.ERROR);
            }

            // Parent Task
            logger.info("composed-task-failedCTRRetry-test:check parent");
            assertThat(task.executions().size()).isEqualTo(1);
            Optional<TaskExecutionResource> taskExecutionResource = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(taskExecutionResource).isPresent();
            assertThat(taskExecutionResource.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));
            logger.info("composed-task-failedCTRRetry-test:check children");
            // Failed tasks
            childTasksBySuffix(task, "b1").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(launch.getExecutionId(), launch.getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_ERROR);
            });
            logger.info("composed-task-failedCTRRetry-test:check not run");
            // Not run tasks
            childTasksBySuffix(task, "t1").forEach(childTask ->
                assertThat(childTask.executions().size()).isEqualTo(0)
            );

            // Parent Task
            assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);
            logger.info("composed-task-failedCTRRetry-test:restart");
            // restart job
            assertThat(task.executions().size()).isEqualTo(1);
            Optional<TaskExecutionResource> executionResource = task.executions().stream().findFirst();
            assertThat(executionResource).isPresent();
            List<Long> jobExecutionIds = executionResource.get().getJobExecutionIds();
            assertThat(jobExecutionIds.size()).isEqualTo(1);
            dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0), executionResource.get().getSchemaTarget());

            Optional<TaskExecutionResource> resource = task.executions().stream().max(Comparator.comparingLong(TaskExecutionResource::getExecutionId));

            assertThat(resource).isPresent();
            logger.info("composed-task-failedCTRRetry-test:wait complete");
            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(resource.get().getExecutionId(), resource.get().getSchemaTarget()) == TaskExecutionStatus.COMPLETE);

            assertThat(task.executions().size()).isEqualTo(2);
            Optional<TaskExecutionResource> execution = task.execution(resource.get().getExecutionId(), resource.get().getSchemaTarget());
            assertThat(execution).isPresent();
            assertThat(execution.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            logger.info("composed-task-failedCTRRetry-test:wait children");
            childTasksBySuffix(task, "b1").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(2);
                assertThat(childTask.executionByParentExecutionId(resource.get().getExecutionId(), resource.get().getSchemaTarget())
                    .get()
                    .getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });

            childTasksBySuffix(task, "t1").forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                Optional<TaskExecutionResource> child = childTask.executionByParentExecutionId(resource.get().getExecutionId(),
                    resource.get().getSchemaTarget());
                assertThat(child).isPresent();
                assertThat(child.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
            });
            assertThat(task.jobExecutionResources().size()).isEqualTo(2);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);

    }

    @Test
    @Tag("group6")
    public void basicBatchSuccessTest() {
        // Verify Batch runs successfully
        logger.info("basic-batch-success-test");
        try (Task task = Task.builder(dataFlowOperations).name(randomTaskName()).definition("scenario").description("Test scenario batch app").build()) {

            String stepName = randomStepName();
            List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
            // task first launch
            LaunchResponseResource launch = task.launch(args);
            // Verify task
            validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());

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

    private TaskExecutionResource validateSuccessfulTaskLaunch(Task task, long launchId, String schemaTarget) {
        return validateSuccessfulTaskLaunch(task, launchId, schemaTarget, 1);
    }

    private TaskExecutionResource validateSuccessfulTaskLaunch(Task task, long launchId, String schemaTarget, int sizeExpected) {
        Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
            .until(() -> task.executionStatus(launchId, schemaTarget) == TaskExecutionStatus.COMPLETE);
        assertThat(task.executions().size()).isEqualTo(sizeExpected);
        Optional<TaskExecutionResource> taskExecution = task.execution(launchId, schemaTarget);
        assertThat(taskExecution).isPresent();
        assertThat(taskExecution.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
        return taskExecution.get();
    }

    private void verifySuccessfulJobAndStepScenario(Task task, String stepName) {
        assertThat(task.executions().size()).isEqualTo(1);
        List<Long> jobExecutionIds = task.executions().stream().findFirst().get().getJobExecutionIds();
        assertThat(jobExecutionIds.size()).isEqualTo(1);
        // Verify that steps can be retrieved
        task.jobExecutionResources().stream().filter(jobExecution -> jobExecution.getName().equals(task.getTaskName())).forEach(jobExecutionResource -> {
            assertThat(jobExecutionResource.getStepExecutionCount()).isEqualTo(1);
            task.jobStepExecutions(jobExecutionResource.getExecutionId(), jobExecutionResource.getSchemaTarget()).forEach(stepExecutionResource ->
                assertThat(stepExecutionResource.getStepExecution().getStepName()).isEqualTo(stepName)
            );
        });
    }

    private String randomStepName() {
        return "step" + randomSuffix();
    }

    @Test
    @Tag("group1")
    public void basicBatchSuccessRestartTest() {
        // Verify that batch restart on success fails
        try (Task task = Task.builder(dataFlowOperations).name(randomTaskName()).definition("scenario").description("Test scenario batch app").build()) {

            String stepName = randomStepName();
            List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
            // task first launch
            LaunchResponseResource launch = task.launch(args);
            // Verify task and Job
            validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
            verifySuccessfulJobAndStepScenario(task, stepName);

            // Attempt a job restart
            Optional<TaskExecutionResource> taskExecutionResource = task.executions().stream().findFirst();
            assertThat(taskExecutionResource).isPresent();
            final List<Long> jobExecutionIds = taskExecutionResource.get().getJobExecutionIds();

            // There is an Error deserialization issue related to backward compatibility with SCDF
            // 2.6.x
            // The Exception thrown by the 2.6.x servers can not be deserialized by the
            // VndErrorResponseErrorHandler in 2.8+ clients.
            Assumptions.assumingThat(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"), () ->
                assertThatThrownBy(() ->
                    dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0), launch.getSchemaTarget())
                ).isInstanceOf(DataFlowClientException.class)
                    .hasMessageContaining(" and state 'COMPLETED' is not restartable")
            );
        }
    }

    @Test
    @Tag("group6")
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
            LaunchResponseResource launch = task.launch(args);

            // Verify task
            TaskExecutionResource taskExecutionResource = validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(taskExecutionResource).isNotNull();
            List<Long> jobExecutionIds = taskExecutionResource.getJobExecutionIds();
            // There is an Error deserialization issue related to backward compatibility with SCDF
            // 2.6.x
            // The Exception thrown by the 2.6.x servers can not be deserialized by the
            // VndErrorResponseErrorHandler in 2.8+ clients.
            Assumptions.assumingThat(runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.7.0"), () -> {
                dataFlowOperations.jobOperations().executionRestart(jobExecutionIds.get(0), launch.getSchemaTarget());
                // Wait for job to start
                Awaitility.await("task " + task.getTaskName() + " has 2 executions")
                    .until(() -> task.thinkJobExecutionResources().size() == 2);
                // Wait for task for the job to complete
                Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                    .until(() -> {
                        Optional<TaskExecutionResource> executionResource = task.executions().stream().findFirst();
                        return executionResource.filter(resource -> resource.getTaskExecutionStatus() == TaskExecutionStatus.COMPLETE).isPresent();
                    });
                Collection<JobExecutionThinResource> resources = task.thinkJobExecutionResources();
                assertThat(resources.size()).isEqualTo(2);

                Set<BatchStatus> batchStatuses = resources.stream()
                    .map(JobExecutionThinResource::getStatus)
                    .collect(Collectors.toSet());

                assertThat(batchStatuses).contains(BatchStatus.FAILED);
                assertThat(batchStatuses).contains(BatchStatus.COMPLETED);
            });
        }
    }

    @Test
    @Tag("group1")
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

        LaunchResponseResource launch = task.launch();

        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        registerNewTimestampVersion();
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
        launch = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 2);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER);
    }

    @Test
    @Tag("group3")
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
        LaunchResponseResource launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
    }

    @Test
    @Tag("group6")
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
        LaunchResponseResource launch = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        assertThat(task.execution(launch.getExecutionId(), launch.getSchemaTarget()).get().getResourceUrl()).contains(TEST_VERSION_NUMBER);

        launch = task.launch(Collections.singletonMap("version.testtimestamp", CURRENT_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 2);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
    }

    @Test
    @Tag("group5")
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
        LaunchResponseResource launch = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER);

        launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 2);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER, 2);
    }

    @Test
    @Tag("group4")
    public void testCreateTaskWithOneVersionLaunchInvalidVersion() {
        // Scenario: I want to create a task app with 1 version run invalid version
        // Given A task with 1 versions
        // And I create a task definition
        // When I launch task definition using version 2 of app
        // Then Task should fail
        minimumVersionCheck("testCreateTaskWithOneVersionLaunchInvalidVersion");
        try (Task task = createTaskDefinition()) {
            assertThatThrownBy(() ->
                task.launch(Collections.singletonMap("version.testtimestamp", "1.0.100"), null)
            ).isInstanceOf(DataFlowClientException.class)
                .hasMessageContaining("Unknown task app: testtimestamp");
        }
    }

    @Test
    @Tag("group5")
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
        assertThatThrownBy(() ->
            task.launch(Collections.singletonMap("version.testtimestamp", "1.0.2"), null)
        ).isInstanceOf(DataFlowClientException.class)
            .hasMessageContaining("Unknown task app: testtimestamp");

        LaunchResponseResource launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 1);
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER, 1);
    }

    @Test
    @Tag("group5")
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

        LaunchResponseResource launch = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
        logger.info("launched:{},{}", launch.getExecutionId(), launch.getSchemaTarget());
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        resetTimestampVersion();
        assertThatThrownBy(() -> {
            LaunchResponseResource launchResponse = task.launch(Collections.singletonMap("version.testtimestamp", TEST_VERSION_NUMBER), null);
            logger.info("launched:{},{}", launchResponse.getExecutionId(), launchResponse.getSchemaTarget());
        }).isInstanceOf(DataFlowClientException.class)
            .hasMessageContaining("Unknown task app: testtimestamp");
    }

    @Test
    @Tag("group3")
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

        LaunchResponseResource launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);

        registerNewTimestampVersion();
        setDefaultVersionForTimestamp(TEST_VERSION_NUMBER);
        launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 2);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER);
    }

    @Test
    @Tag("group1")
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
        LaunchResponseResource launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);

        setDefaultVersionForTimestamp(TEST_VERSION_NUMBER);
        launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 2);
        validateSpecifiedVersion(task, TEST_VERSION_NUMBER);

        task = createTaskDefinition();
        setDefaultVersionForTimestamp(CURRENT_VERSION_NUMBER);
        launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
    }

    @Test
    @Tag("group2")
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
        LaunchResponseResource launch = task.launch();
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        validateSpecifiedVersion(task, CURRENT_VERSION_NUMBER);
        AppRegistryOperations appRegistryOperations = this.dataFlowOperations.appRegistryOperations();
        appRegistryOperations.unregister("testtimestamp", ApplicationType.task, CURRENT_VERSION_NUMBER);

        assertThatThrownBy(task::launch)
            .isInstanceOf(DataFlowClientException.class)
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
            .description(String.format("Test task definition %s using for app definition\"%s\"", taskDefName, definition))
            .build();
    }

    private void minimumVersionCheck(String testName) {
        Assumptions.assumeTrue(!runtimeApps.dataflowServerVersionLowerThan("2.8.0"), testName + ": SKIP - SCDF 2.7.x and below!");
    }

    private boolean supportBoot3Jobs() {
        return runtimeApps.dataflowServerVersionEqualOrGreaterThan("2.11.0");
    }

    private void registerNewTimestampVersion() {
        registerTimestamp(TEST_VERSION_NUMBER);
    }

    private void registerTimestamp(String versionNumber) {
        if (this.runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            registerTask("testtimestamp", "docker:springcloudtask/timestamp-task", versionNumber, AppBootSchemaVersion.BOOT2);
        } else {
            registerTask("testtimestamp", "maven://io.spring:timestamp-task", versionNumber, AppBootSchemaVersion.BOOT2);
        }
    }

    private void setDefaultVersionForTimestamp(String version) {
        AppRegistryOperations appRegistryOperations = this.dataFlowOperations.appRegistryOperations();
        appRegistryOperations.makeDefault("testtimestamp", ApplicationType.task, version);
    }

    private void assertTaskRegistration(String name, AppBootSchemaVersion bootVersion) {
        try {
            AppRegistryOperations appRegistryOperations = this.dataFlowOperations.appRegistryOperations();
            DetailedAppRegistrationResource resource = appRegistryOperations.info(name, ApplicationType.task, false);
            logger.info("assertTaskRegistration:{}:{}", name, resource.getLinks());
            assertThat(resource).isNotNull();
            assertThat(resource.getBootVersion()).isEqualTo(bootVersion);
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
            registerTask("testtimestamp", "docker:springcloudtask/timestamp-task", CURRENT_VERSION_NUMBER, AppBootSchemaVersion.BOOT2);
        } else {
            registerTask("testtimestamp", "maven://io.spring:timestamp-task", CURRENT_VERSION_NUMBER, AppBootSchemaVersion.BOOT2);
        }
        setDefaultVersionForTimestamp(CURRENT_VERSION_NUMBER);
    }

    private void validateSpecifiedVersion(Task task, String version) {
        TaskExecutionResource last = task.executions()
            .stream()
            .max(Comparator.comparing(TaskExecutionResource::getEndTime))
            .orElseThrow(() -> new RuntimeException("Cannot find task:" + task.getTaskName()));
        int start = last.getResourceUrl().indexOf('[');
        int end = last.getResourceUrl().indexOf(']');
        if (start >= 0 && end >= 0 && start < end) {
            final String uri = last.getResourceUrl().substring(start + 1, end);
            assertThat(uri).endsWith(":" + version);
        } else {
            assertThat(last.getResourceUrl()).contains(version);
        }
    }

    private void validateSpecifiedVersion(Task task, String version, int countExpected) {
        assertThat(task.executions()
            .stream()
            .filter(taskExecutionResource -> taskExecutionResource.getResourceUrl().contains(version))
            .count()).isEqualTo(countExpected);
    }

    @Test
    @Tag("group5")
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
            LaunchResponseResource launch = task.launch(Collections.singletonMap(testPropertyKey, testPropertyValue), args);
            // Verify task
            validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
            LaunchResponseResource launch1 = task.launch(args);
            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(launch1.getExecutionId(), launch1.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(
                task.executions()
                    .stream()
                    .filter(te -> te.getDeploymentProperties().containsKey(testPropertyKey))
                    .count()
            ).isEqualTo(2);

        }
    }

    @Test
    @Tag("group5")
    public void taskLaunchInvalidTaskDefinition() {
        logger.info("task-launch-invalid-task-definition");
        assertThatThrownBy(() ->
            Task.builder(dataFlowOperations)
                .name(randomTaskName())
                .definition("foobar")
                .description("Test scenario with invalid task definition")
                .build()
        ).isInstanceOf(DataFlowClientException.class)
            .hasMessageContaining("The 'task:foobar' application could not be found.");
    }

    @Test
    @Tag("group1")
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
            LaunchResponseResource launch = task.launch(args);
            // Verify first launch
            validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
            // relaunch task with no args and it should not re-use old.
            LaunchResponseResource launch1 = task.launch(baseArgs);
            Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                .until(() -> task.executionStatus(launch1.getExecutionId(), launch1.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(2);
            assertThat(
                task.executions()
                    .stream()
                    .filter(execution -> execution.getArguments().contains(argument))
                    .count()
            ).isEqualTo(1);
        }

    }

    @Test
    @Tag("group4")
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
            LaunchResponseResource launch = task.launch(args);

            validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(dataFlowOperations.taskOperations().list().getContent().size()).isEqualTo(1);
        }
        verifyTaskDefAndTaskExecutionCount(taskName, 0, 1);
    }

    @Test
    @Tag("group1")
    public void taskDefinitionDeleteWithCleanup() {
        Task task = Task.builder(dataFlowOperations)
            .name(randomTaskName())
            .definition("scenario")
            .description("Test scenario batch app that will fail on first pass")
            .build();
        String stepName = randomStepName();
        List<String> args = createNewJobandStepScenario(task.getTaskName(), stepName);
        // task first launch
        LaunchResponseResource launch = task.launch(args);
        // Verify task
        validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget());
        // verify task definition is gone and executions are removed
        this.dataFlowOperations.taskOperations().destroy(task.getTaskName(), true);
        verifyTaskDefAndTaskExecutionCount(task.getTaskName(), 0, 0);
    }

    @Test
    @Tag("group6")
    public void testDeleteSingleTaskExecution() {
        // Scenario: I want to delete a single task execution
        // Given A task definition exists
        // And 1 task execution exist
        // When I delete a task execution
        // Then It should succeed
        // And I will not see the task executions
        minimumVersionCheck("testDeleteSingleTaskExecution");
        try (Task task = createTaskDefinition()) {
            List<LaunchResponse> launches = createTaskExecutionsForDefinition(task, 1);
            verifyAllSpecifiedTaskExecutions(task, launches, true);
            safeCleanupTaskExecution(task, launches.get(0).getExecutionId(), launches.get(0).getSchemaTarget());
            verifyAllSpecifiedTaskExecutions(task, launches, false);
        }
    }

    @Test
    @Tag("group3")
    public void testDeleteMultipleTaskExecution() {
        // Scenario: I want to delete 3 task executions
        // Given A task definition exists
        // And 4 task execution exist
        // When I delete 3 task executions
        // Then They should succeed
        // And I will see the remaining task execution
        minimumVersionCheck("testDeleteMultipleTaskExecution");
        try (Task task = createTaskDefinition()) {
            List<LaunchResponse> launchIds = createTaskExecutionsForDefinition(task, 4);
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            LaunchResponse retainedLaunch = launchIds.get(3);
            launchIds.stream()
                .filter(launch -> !launch.equals(retainedLaunch))
                .forEach(launch -> {
                    safeCleanupTaskExecution(task, launch.getExecutionId(), launch.getSchemaTarget());
                    assertThatThrownBy(() ->
                        task.execution(launch.getExecutionId(), launch.getSchemaTarget())
                    ).isInstanceOf(DataFlowClientException.class);
                });
            assertThat(task.execution(retainedLaunch.getExecutionId(), retainedLaunch.getSchemaTarget())).isPresent();
        }
    }

    @Test
    @Tag("group3")
    public void testDeleteAllTaskExecutionsShouldClearAllTaskExecutions() {
        // Scenario: Delete all task executions should clear all task executions
        // Given A task definition exists
        // And 4 task execution exist
        // When I delete all task executions
        // Then It should succeed
        // And I will not see the task executions
        minimumVersionCheck("testDeleteAllTaskExecutionsShouldClearAllTaskExecutions");
        try (Task task = createTaskDefinition()) {
            List<LaunchResponse> launchIds = createTaskExecutionsForDefinition(task, 4);
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            safeCleanupAllTaskExecutions(task);
            verifyAllSpecifiedTaskExecutions(task, launchIds, false);
        }
    }

    @Test
    @Tag("group4")
    public void testDataFlowUsesLastAvailableTaskExecutionForItsProperties() {
        // Scenario: Task Launch should use last available task execution for its properties
        // Given A task definition exists
        // And 2 task execution exist each having different properties
        // When I launch task definition using default app version
        // Then It should succeed
        // And The task execution will contain the properties from both task executions
        minimumVersionCheck("testDataFlowUsesLastAvailableTaskExecutionForItsProperties");
        try (Task task = createTaskDefinition()) {
            List<LaunchResponse> firstLaunchIds = createTaskExecutionsForDefinition(task,
                Collections.singletonMap("app.testtimestamp.firstkey", "firstvalue"),
                1);
            verifyAllSpecifiedTaskExecutions(task, firstLaunchIds, true);
            LaunchResponseResource launchResponse = task.launch();
            assertThat(task.execution(launchResponse.getExecutionId(), launchResponse.getSchemaTarget()))
                .withFailMessage("expected task execution for " + launchResponse.getExecutionId() + ":" + launchResponse.getSchemaTarget())
                .isPresent();
            validateSuccessfulTaskLaunch(task, launchResponse.getExecutionId(), launchResponse.getSchemaTarget(), 2);
            Optional<TaskExecutionResource> taskExecution = task.execution(launchResponse.getExecutionId(), launchResponse.getSchemaTarget());
            assertThat(taskExecution).isPresent();
            Map<String, String> properties = taskExecution.get().getAppProperties();
            assertThat(properties).containsKey("firstkey");
        }
    }

    @Test
    @Tag("group3")
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
            List<LaunchResponse> firstLaunchIds = createTaskExecutionsForDefinition(task,
                Collections.singletonMap("app.testtimestamp.firstkey", "firstvalue"),
                1);
            verifyAllSpecifiedTaskExecutions(task, firstLaunchIds, true);
            LaunchResponseResource launch = task.launch(Collections.singletonMap("app.testtimestamp.secondkey", "secondvalue"),
                Collections.emptyList());
            assertThat(task.execution(launch.getExecutionId(), launch.getSchemaTarget()))
                .withFailMessage("expected task execution for " + launch.getExecutionId() + ":" + launch.getSchemaTarget())
                .isPresent();
            validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 2);
            safeCleanupTaskExecution(task, launch.getExecutionId(), launch.getSchemaTarget());
            assertThatThrownBy(() ->
                task.execution(launch.getExecutionId(), launch.getSchemaTarget())
            ).isInstanceOf(DataFlowClientException.class);

            LaunchResponseResource thirdResponse = task.launch(Collections.singletonMap("app.testtimestamp.thirdkey", "thirdvalue"), Collections.emptyList());
            assertThat(task.execution(thirdResponse.getExecutionId(), thirdResponse.getSchemaTarget()))
                .withFailMessage("expected task execution for " + thirdResponse.getExecutionId() + ":" + thirdResponse.getSchemaTarget())
                .isPresent();
            validateSuccessfulTaskLaunch(task, thirdResponse.getExecutionId(), thirdResponse.getSchemaTarget(), 2);
            Optional<TaskExecutionResource> taskExecution = task.execution(thirdResponse.getExecutionId(), thirdResponse.getSchemaTarget());
            assertThat(taskExecution).isPresent();
            Map<String, String> properties = taskExecution.get().getAppProperties();
            assertThat(properties).containsKey("firstkey");
            assertThat(properties).doesNotContainKey("secondkey");
            assertThat(properties).containsKey("thirdkey");

        }
    }

    @Test
    @Tag("group4")
    public void testDeletingComposedTaskExecutionDeletesAllItsChildTaskExecutions() {
        // Deleting a Composed Task Execution deletes all of its child task executions
        // Given A composed task definition exists of "AAA && BBB"
        // And 1 task execution exist
        // And I delete the last task execution
        // Then It should succeed
        // And I will not see the composed task executions
        minimumVersionCheck("testDeletingComposedTaskExecutionDeletesAllItsChildTaskExecutions");
        try (Task task = createTaskDefinition("AAA: testtimestamp && BBB: testtimestamp")) {
            List<LaunchResponse> launchIds = createTaskExecutionsForDefinition(task, 1);
            verifyAllSpecifiedTaskExecutions(task, launchIds, true);
            Optional<TaskExecutionResource> aaaExecution = task.composedTaskChildExecution("AAA");
            Optional<TaskExecutionResource> bbbExecution = task.composedTaskChildExecution("BBB");
            assertThat(aaaExecution).isPresent();
            assertThat(bbbExecution).isPresent();
            safeCleanupTaskExecution(task, launchIds.get(0).getExecutionId(), launchIds.get(0).getSchemaTarget());
            verifyAllSpecifiedTaskExecutions(task, launchIds, false);
            aaaExecution = task.composedTaskChildExecution("AAA");
            bbbExecution = task.composedTaskChildExecution("BBB");
            assertThat(aaaExecution).isNotPresent();
            assertThat(bbbExecution).isNotPresent();
        }

    }

    @Test
    @Tag("group4")
    public void testDeletingBatchTaskExecutionDeletesAllOfItsBatchRecords() {
        // Given A batch task definition exists
        // And 1 task execution exist
        // When I delete the last task execution
        // Then It should succeed
        // And I will not see the task executions
        // And I will not see the batch executions
        minimumVersionCheck("testDeletingBatchTaskExecutionDeletesAllOfItsBatchRecords");
        try (Task task = createTaskDefinition("testtimestamp-batch")) {
            LaunchResponseResource launch = task.launch(Collections.emptyMap(), Collections.singletonList("testKey=" + task.getTaskName()));
            List<LaunchResponse> launches = Collections.singletonList(new LaunchResponse(launch.getExecutionId(), launch.getSchemaTarget()));
            verifyAllSpecifiedTaskExecutions(task, launches, true);
            validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 1);

            Optional<TaskExecutionResource> taskExecution = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(taskExecution).isPresent();
            List<Long> jobExecutionIds = taskExecution.get().getJobExecutionIds();
            assertThat(jobExecutionIds.size()).isEqualTo(2);
            assertThat(task.jobStepExecutions(jobExecutionIds.get(0), taskExecution.get().getSchemaTarget()).size()).isEqualTo(1);
            safeCleanupTaskExecution(task, launch.getExecutionId(), launch.getSchemaTarget());
            verifyAllSpecifiedTaskExecutions(task, launches, false);
            assertThatThrownBy(() ->
                task.jobStepExecutions(jobExecutionIds.get(0), launch.getSchemaTarget())
            ).isInstanceOf(DataFlowClientException.class)
                .hasMessageContaining("No JobExecution with id=");
        }
    }

    @Test
    @Tag("group4")
    public void testDeletingBatchTaskExecutionDeletesAllOfItsBatchRecordsBoot3() {
        // Given A batch task definition exists
        // And 1 task execution exist
        // When I delete the last task execution
        // Then It should succeed
        // And I will not see the task executions
        // And I will not see the batch executions
        if (supportBoot3Jobs()) {
            try (Task task = createTaskDefinition("testtimestamp-batch3")) {
                LaunchResponseResource launch = task.launch(Collections.emptyMap(), Collections.singletonList("testKey=" + task.getTaskName()));
                List<LaunchResponse> launches = Collections.singletonList(new LaunchResponse(launch.getExecutionId(), launch.getSchemaTarget()));
                verifyAllSpecifiedTaskExecutions(task, launches, true);
                validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 1);

                Optional<TaskExecutionResource> taskExecution = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
                assertThat(taskExecution).isPresent();
                List<Long> jobExecutionIds = taskExecution.get().getJobExecutionIds();
                assertThat(jobExecutionIds.size()).isEqualTo(2);
                assertThat(task.jobStepExecutions(jobExecutionIds.get(0), taskExecution.get().getSchemaTarget()).size()).isEqualTo(1);
                safeCleanupTaskExecution(task, launch.getExecutionId(), launch.getSchemaTarget());
                verifyAllSpecifiedTaskExecutions(task, launches, false);
                assertThatThrownBy(() ->
                    task.jobStepExecutions(jobExecutionIds.get(0), launch.getSchemaTarget())
                ).isInstanceOf(DataFlowClientException.class)
                    .hasMessageContaining("No JobExecution with id=");
            }
        } else {
            logger.warn("skipping boot3 workload for " + runtimeApps.getDataflowServerVersion());
        }
    }

    @Test
    @Tag("group3")
    public void testRestartingBatchTaskExecutionThatHasBeenDeleted() {
        // Restarting a Batch Task Execution that has been deleted
        // Given A batch task definition exists
        // And 1 task execution exist
        // And I delete the last task execution
        // When I restart the batch job
        // And The batch job will fail
        minimumVersionCheck("testRestartingBatchTaskExecutionThatHasBeenDeleted");
        try (Task task = createTaskDefinition("testtimestamp-batch")) {
            LaunchResponseResource launch = task.launch(Collections.emptyMap(), Collections.singletonList("testKey=" + task.getTaskName()));
            verifyAllSpecifiedTaskExecutions(task, Collections.singletonList(new LaunchResponse(launch.getExecutionId(), launch.getSchemaTarget())), true);
            validateSuccessfulTaskLaunch(task, launch.getExecutionId(), launch.getSchemaTarget(), 1);

            Optional<TaskExecutionResource> taskExecution = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(taskExecution).isPresent();
            assertThat(taskExecution.get().getJobExecutionIds().size()).isEqualTo(2);
            Long jobExecutionId = taskExecution.get().getJobExecutionIds().get(0);
            assertThat(task.jobStepExecutions(jobExecutionId, launch.getSchemaTarget()).size()).isEqualTo(1);
            safeCleanupTaskExecution(task, launch.getExecutionId(), launch.getSchemaTarget());
            assertThatThrownBy(() ->
                this.dataFlowOperations.jobOperations().executionRestart(jobExecutionId, launch.getSchemaTarget())
            ).isInstanceOf(DataFlowClientException.class);
        }

    }

    private List<LaunchResponse> createTaskExecutionsForDefinition(Task task, int executionCount) {
        return createTaskExecutionsForDefinition(task, Collections.emptyMap(), executionCount);
    }

    private List<LaunchResponse> createTaskExecutionsForDefinition(
        Task task, Map<String, String> properties, int executionCount
    ) {
        List<LaunchResponse> launchIds = new ArrayList<>();
        for (int i = 0; i < executionCount; i++) {
            LaunchResponseResource responseResource = task.launch(properties, Collections.emptyList());
            launchIds.add(new LaunchResponse(responseResource.getExecutionId(), responseResource.getSchemaTarget()));
            assertThat(task.execution(responseResource.getExecutionId(), responseResource.getSchemaTarget()))
                .withFailMessage("expected task execution for " + responseResource.getExecutionId() + ":" + responseResource.getSchemaTarget())
                .isPresent();
            validateSuccessfulTaskLaunch(task, responseResource.getExecutionId(), responseResource.getSchemaTarget(), i + 1);
        }
        return launchIds;
    }

    private void verifyAllSpecifiedTaskExecutions(Task task, List<LaunchResponse> launches, boolean isPresent) {
        launches.forEach(launch -> {
            if (isPresent) {
                assertThat(task.execution(launch.getExecutionId(), launch.getSchemaTarget()))
                    .withFailMessage("verifyAllSpecifiedTaskExecutions expected task execution fort :" + launch)
                    .isPresent();
            } else {
                try {
                    assertThat(task.execution(launch.getExecutionId(), launch.getSchemaTarget()))
                        .withFailMessage("verifyAllSpecifiedTaskExecutions expected no task execution for :" + launch)
                        .isNotPresent();
                } catch (DataFlowClientException x) {
                    logger.warn("verifyAllSpecifiedTaskExecutions:exception:" + x);
                }
            }
        });
    }

    private void verifyTaskDefAndTaskExecutionCount(final String taskName, int taskDefCount, int taskExecCount) {
        Awaitility.await("task " + taskName + " has " + taskDefCount + " definitions and " + taskExecCount + " executions")
            .atMost(60, TimeUnit.SECONDS)
            .until(() -> {
                List<TaskExecutionResource> executions = dataFlowOperations.taskOperations()
                    .executionList()
                    .getContent()
                    .stream()
                    .filter(taskExecution -> taskExecution.getTaskName() != null && taskExecution.getTaskName().equals(taskName))
                    .collect(Collectors.toList());
                List<TaskDefinitionResource> definitions = dataFlowOperations.taskOperations()
                    .list()
                    .getContent()
                    .stream()
                    .filter(taskDefinitionResource -> taskDefinitionResource.getName().equals(taskName))
                    .collect(Collectors.toList());
                return (executions.size() == taskExecCount) && (definitions.size() == taskDefCount);
            });
    }

    private void allSuccessfulExecutions(String taskDescription, String taskDefinition, String... childLabels) {
        mixedSuccessfulFailedAndUnknownExecutions(taskDescription, taskDefinition, TaskExecutionStatus.COMPLETE, asList(childLabels), emptyList(), emptyList());
    }

    private void mixedSuccessfulFailedAndUnknownExecutions(
        String taskDescription,
        String taskDefinition,
        TaskExecutionStatus parentTaskExecutionStatus,
        List<String> successfulTasks,
        List<String> failedTasks,
        List<String> unknownTasks
    ) {

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder.name(randomTaskName()).definition(taskDefinition).description(taskDescription).build()) {

            ArrayList<String> allTasks = new ArrayList<>(successfulTasks);
            allTasks.addAll(failedTasks);
            allTasks.addAll(unknownTasks);

            assertThat(task.composedTaskChildTasks().size()).isEqualTo(allTasks.size());
            assertThat(task.composedTaskChildTasks().stream().map(Task::getTaskName).collect(Collectors.toList())).as(
                "verify composedTaskChildTasks is the same as all tasks").hasSameElementsAs(fullTaskNames(task, allTasks.toArray(new String[0])));

            LaunchResponseResource launch = task.launch(composedTaskLaunchArguments());

            if (runtimeApps.dataflowServerVersionLowerThan("2.8.0-SNAPSHOT")) {
                Awaitility.await("task " + task.getTaskName() + " is COMPLETE")
                    .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            } else {
                Awaitility.await("task " + task.getTaskName() + " is ERROR")
                    .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == parentTaskExecutionStatus);
            }

            // Parent Task
            assertThat(task.executions().size()).as("verify exactly one execution").isEqualTo(1);
            assertThat(task.execution(launch.getExecutionId(), launch.getSchemaTarget()).get().getExitCode()).as("verify successful execution of parent task")
                .isEqualTo(EXIT_CODE_SUCCESS);
            task.executions()
                .forEach(execution -> assertThat(execution.getExitCode()).as("verify successful execution of parent task").isEqualTo(EXIT_CODE_SUCCESS));

            // Successful tasks
            childTasksBySuffix(task, successfulTasks.toArray(new String[0])).forEach(childTask -> {
                assertThat(childTask.executions().size()).as("verify each child task ran once").isEqualTo(1);
                Optional<TaskExecutionResource> taskExecutionResource = childTask.executionByParentExecutionId(launch.getExecutionId(),
                    launch.getSchemaTarget());
                assertThat(taskExecutionResource).isPresent().as("verify each child task has a parent");
                assertThat(taskExecutionResource.get().getExitCode()).as("verify each child task has a successful parent")
                    .isEqualTo(EXIT_CODE_SUCCESS);
            });

            // Failed tasks
            childTasksBySuffix(task, failedTasks.toArray(new String[0])).forEach(childTask -> {
                assertThat(childTask.executions().size()).isEqualTo(1);
                assertThat(childTask.executionByParentExecutionId(launch.getExecutionId(), launch.getSchemaTarget()).get().getExitCode()).isEqualTo(
                    EXIT_CODE_ERROR);
            });

            // Not run tasks
            childTasksBySuffix(task, unknownTasks.toArray(new String[0])).forEach(childTask ->
                assertThat(childTask.executions().size()).isEqualTo(0)
            );

            // Parent Task
            assertThat(taskBuilder.allTasks().size()).isEqualTo(task.composedTaskChildTasks().size() + 1);
        }
        assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
    }

    private List<String> fullTaskNames(Task task, String... childTaskNames) {
        return java.util.stream.Stream.of(childTaskNames).map(cn -> task.getTaskName() + "-" + cn.trim()).collect(Collectors.toList());
    }

    private List<Task> childTasksBySuffix(Task task, String... suffixes) {
        return java.util.stream.Stream.of(suffixes).map(suffix -> task.composedTaskChildTaskByLabel(suffix).get()).collect(Collectors.toList());
    }

    private void safeCleanupAllTaskExecutions(Task task) {
        doSafeCleanupTasks(task::cleanupAllTaskExecutions);
    }

    private void safeCleanupTaskExecution(Task task, long taskExecutionId, String schemaTarget) {
        logger.info("safeCleanupTaskExecution:{}:{}", taskExecutionId, schemaTarget);
        doSafeCleanupTasks(() -> task.cleanupTaskExecution(taskExecutionId, schemaTarget));
    }

    private void doSafeCleanupTasks(Runnable cleanupOperation) {
        try {
            cleanupOperation.run();
        } catch (DataFlowClientException ex) {
            if (ex.getMessage().contains("(reason: pod does not exist)") || ex.getMessage().contains("(reason: job does not exist)")) {
                logger.warn("Unable to cleanup task executions: " + ex.getMessage());
            } else {
                logger.error("doSafeCleanupTasks:exception:" + ex, ex);
                throw ex;
            }
        }
    }

    private static String randomTaskName() {
        return "task" + randomSuffix();
    }

    private static String randomJobName() {
        return "job" + randomSuffix();
    }

    private static String randomSuffix() {
        String result = Integer.toString(new Random(System.nanoTime()).nextInt());
        return !result.startsWith("-") ? "-" + result : result;
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
    @Tag("group3")
    @Tag("smoke")
    public void streamWithConfigServer() {
        logger.info("stream-server-config-test");

        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("TICKTOCK-config-server" + randomSuffix())
            .definition("time | log")
            .create()
            .deploy(new DeploymentPropertiesBuilder().putAll(testDeploymentProperties("log", "time"))
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

    @Test
    @Tag("always")
    void checkEndpoints() {
        AboutResource aboutResource = dataFlowOperations.aboutOperation().get();
        assertThat(aboutResource).isNotNull();
        logger.info("authenticated:{}", aboutResource.getSecurityInfo().isAuthenticated());
        logger.info("testing:tasks/executions");
        assertThat(dataFlowOperations.taskOperations().executionList()).isNotNull();
        String version = aboutResource.getVersionInfo().getCore().getVersion();
        if (VersionUtils.isDataFlowServerVersionGreaterThanOrEqualToRequiredVersion(VersionUtils.getThreePartVersion(version), "2.11.3")) {
            logger.info("testing:tasks/thinexecutions");
            assertThat(dataFlowOperations.taskOperations().thinExecutionList()).isNotNull();
        }
        // TODO add new endpoints
    }
    @Test
    @Tag("groupF")
    void willFail() {
        fail("Fails on purpose");
    }
}
