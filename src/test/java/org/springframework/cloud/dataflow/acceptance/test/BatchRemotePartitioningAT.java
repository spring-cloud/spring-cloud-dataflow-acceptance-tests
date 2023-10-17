/*
 * Copyright 2020-2022 the original author or authors.
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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.dsl.task.Task;
import org.springframework.cloud.dataflow.rest.client.dsl.task.TaskBuilder;
import org.springframework.cloud.dataflow.rest.resource.LaunchResponseResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionStatus;
import org.springframework.cloud.deployer.spi.cloudfoundry.CloudFoundryConnectionProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes acceptance tests for the batch remote partition task.
 *
 * @author David Turanski
 * @author Glenn Renfro
 * @author Christian Tzolov
 * @author Corneil du Plessis
 */
@ExtendWith(SpringExtension.class)
public class BatchRemotePartitioningAT extends CommonTestBase {
    private static final Logger logger = LoggerFactory.getLogger(BatchRemotePartitioningAT.class);

    private static final String TASK_NAME = "batch-remote-partition";
    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_ERROR = 1;

    @Autowired(required = false)
    private CFConnectionProperties cfConnectionProperties;

    @BeforeAll
    public static void beforeAll() {
    }

    @BeforeEach
    public void before() {
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(5));
        Awaitility.setDefaultTimeout(Duration.ofMinutes(10));
        logger.info("[platform = {}, type = {}]", runtimeApps.getPlatformName(), runtimeApps.getPlatformType());

    }

    @AfterEach
    public void after() {
        dataFlowOperations.taskOperations().destroyAll();
    }

    @Test
    @EnabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry")
    @Tag("group2")
    @Tag("smoke")
    public void runBatchRemotePartitionJobCloudFoundry() {
        logger.info("run-batch-remote-partition-job-cloudFoundry:start");
        final String prefix = CFConnectionProperties.CLOUDFOUNDRY_PROPERTIES;
        String taskDefinition = TASK_NAME +
            String.format(" --%s.%s=%s", prefix, "username", cfConnectionProperties.getUsername()) +
            String.format(" --%s.%s=%s", prefix, "password", cfConnectionProperties.getPassword()) +
            String.format(" --%s.%s=%s", prefix, "org", cfConnectionProperties.getOrg()) +
            String.format(" --%s.%s=%s", prefix, "username", cfConnectionProperties.getUsername()) +
            String.format(" --%s.%s=%s", prefix, "space", cfConnectionProperties.getSpace()) +
            String.format(" --%s.%s=%s", prefix, "url", cfConnectionProperties.getUrl().toString()) +
            String.format(" --%s.%s=%s", prefix, "skipSslValidation", cfConnectionProperties.isSkipSslValidation());

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder
            .name(randomName())
            .definition(taskDefinition)
            .description("runBatchRemotePartitionJob - cloudfoundry")
            .build()) {

            LaunchResponseResource launch = task.launch(Collections.EMPTY_MAP, Arrays.asList("--platform=cloudfoundry"));

            Awaitility.await().until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(1);
            Optional<TaskExecutionResource> taskExecution = task.execution(launch.getExecutionId(), launch.getSchemaTarget());
            assertThat(taskExecution).isPresent();
            assertThat(taskExecution.get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
        }
        logger.info("run-batch-remote-partition-job-cloudFoundry:end");
    }

    @Test
    @EnabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "kubernetes")
    @Tag("group2")
    @Tag("smoke")
    public void runBatchRemotePartitionJobKubernetes() {
        logger.info("run-batch-remote-partition-job-kubernetes:start");

        TaskBuilder taskBuilder = Task.builder(dataFlowOperations);
        try (Task task = taskBuilder
            .name(randomName())
            .definition(TASK_NAME)
            .description("runBatchRemotePartitionJob - kubernetes")
            .build()) {

            LaunchResponseResource launch = task.launch(Collections.singletonMap("deployer.*.kubernetes.deployment-service-account-name", testProperties.getPlatform().getConnection().getPlatformName()),
                Arrays.asList("--platform=kubernetes", "--artifact=docker://springcloud/batch-remote-partition:0.0.2-SNAPSHOT"));

            Awaitility.await()
                    .atMost(Duration.ofMinutes(20))
                    .until(() -> task.executionStatus(launch.getExecutionId(), launch.getSchemaTarget()) == TaskExecutionStatus.COMPLETE);
            assertThat(task.executions().size()).isEqualTo(1);
            assertThat(task.execution(launch.getExecutionId(), launch.getSchemaTarget()).isPresent()).isTrue();
            assertThat(task.execution(launch.getExecutionId(), launch.getSchemaTarget()).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
        }
        logger.info("run-batch-remote-partition-job-kubernetes:end");
    }

    private static String randomName() {
        return "task-" + UUID.randomUUID().toString().substring(0, 10);
    }

    @ConfigurationProperties(CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES)
    static class CFConnectionProperties extends CloudFoundryConnectionProperties {
    }

    @Configuration
    @ConditionalOnProperty(value = "PLATFORM_TYPE", havingValue = "cloudfoundry")
    @EnableConfigurationProperties({ BatchRemotePartitioningAT.CFConnectionProperties.class })
    static class ConditionalCloudFoundryTestConfiguration {
    }
}
