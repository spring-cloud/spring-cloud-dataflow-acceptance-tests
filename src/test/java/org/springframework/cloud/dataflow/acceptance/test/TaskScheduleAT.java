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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.dsl.task.Task;
import org.springframework.cloud.dataflow.rest.client.dsl.task.TaskSchedule;
import org.springframework.cloud.dataflow.rest.client.dsl.task.TaskScheduleBuilder;
import org.springframework.cloud.dataflow.rest.resource.ScheduleInfoResource;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerPropertyKeys;
import org.springframework.hateoas.PagedModel;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The scheduler tests are run only if the SCDF Scheduler feature is enabled.
 *
 * @author Christian Tzolov
 * @author Corneil du Plessis
 */
@ExtendWith(SpringExtension.class)
@TaskScheduleAT.AssumeSchedulerEnabled
public class TaskScheduleAT extends CommonTestBase {
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduleAT.class);

    private final static String DEFAULT_CRON_EXPRESSION = "56 20 ? * *";

    /**
     * REST and DSL clients used to interact with the SCDF server and run the tests.
     */
    private String platformInfo;

    @BeforeAll
    public static void beforeAll() {
    }

    @BeforeEach
    public void before() {
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(5));
        Awaitility.setDefaultTimeout(Duration.ofMinutes(10));
        platformInfo = String.format("[platform = %s, type = %s]", runtimeApps.getPlatformName(), runtimeApps.getPlatformType());
        registerTimestampTasks();
    }

    @AfterEach
    public void after() {
        if (dataFlowOperations.schedulerOperations() != null) {
            PagedModel<ScheduleInfoResource> scheduleInfoResources = dataFlowOperations.schedulerOperations().list();
            Iterator<ScheduleInfoResource> scheduleInfoResourceIterator = scheduleInfoResources.iterator();
            ScheduleInfoResource scheduleInfoResource;

            while (scheduleInfoResourceIterator.hasNext()) {
                scheduleInfoResource = scheduleInfoResourceIterator.next();
                logger.info("Test unchedule:" + scheduleInfoResource.getScheduleName());
                dataFlowOperations.schedulerOperations().unschedule(scheduleInfoResource.getScheduleName());
            }
        }
        dataFlowOperations.taskOperations().destroyAll();
    }

    @Test
    @Order(Integer.MIN_VALUE)
    @Tag("all")
    public void testConfigurationInfo() {
        logger.info(platformInfo);
    }

    @Test
    @Tag("group6")
    public void listTest() {
        logger.info("schedule-list-test");

        TaskScheduleBuilder taskScheduleBuilder = TaskSchedule.builder(dataFlowOperations);

        try (Task task1 = Task.builder(dataFlowOperations).name(randomName("task1")).definition("testtimestamp").build();
             Task task2 = Task.builder(dataFlowOperations).name(randomName("task2")).definition("testtimestamp").build();

             TaskSchedule taskSchedule1 = taskScheduleBuilder.scheduleName(randomName("schedule1")).task(task1).build();
             TaskSchedule taskSchedule2 = taskScheduleBuilder.scheduleName(randomName("schedule2")).task(task2).build()) {

            taskSchedule1.schedule(DEFAULT_CRON_EXPRESSION, Collections.emptyMap());
            taskSchedule2.schedule(DEFAULT_CRON_EXPRESSION, Collections.emptyMap());

            assertThat(taskScheduleBuilder.list().size()).isEqualTo(2);

            HashSet<String> scheduleSet = new HashSet<>(Arrays.asList(taskSchedule1.getScheduleName(), taskSchedule2.getScheduleName()));

            for (TaskSchedule taskSchedule : taskScheduleBuilder.list()) {
                if (scheduleSet.contains(taskSchedule.getScheduleName())) {
                    assertThat(taskSchedule.getScheduleProperties().get(SchedulerPropertyKeys.CRON_EXPRESSION)).isEqualTo(DEFAULT_CRON_EXPRESSION);
                }
                else {
                    fail(String.format("%s schedule is missing from result set of list.", taskSchedule.getScheduleName()));
                }
            }
        }
    }

    @Test
    @Tag("group5")
    public void filterByTaskTest() {
        logger.info("schedule-find-by-task-test");

        TaskScheduleBuilder taskScheduleBuilder = TaskSchedule.builder(dataFlowOperations);

        try (Task task1 = Task.builder(dataFlowOperations).name(randomName("task1")).definition("testtimestamp").build();
             Task task2 = Task.builder(dataFlowOperations).name(randomName("task2")).definition("testtimestamp").build();

             TaskSchedule taskSchedule1 = taskScheduleBuilder.scheduleName(randomName("schedule1")).task(task1).build();
             TaskSchedule taskSchedule2 = taskScheduleBuilder.scheduleName(randomName("schedule2")).task(task2).build()) {


            assertThat(taskScheduleBuilder.list().size()).isEqualTo(0);
            assertThat(taskScheduleBuilder.list(task1).size()).isEqualTo(0);
            assertThat(taskScheduleBuilder.list(task2).size()).isEqualTo(0);

            taskSchedule1.schedule(DEFAULT_CRON_EXPRESSION, Collections.emptyMap());
            taskSchedule2.schedule(DEFAULT_CRON_EXPRESSION, Collections.emptyMap());

            assertThat(taskScheduleBuilder.list().size()).isEqualTo(2);
            assertThat(taskScheduleBuilder.list(task1).size()).isEqualTo(1);
            assertThat(taskScheduleBuilder.list(task2).size()).isEqualTo(1);

            assertThat(taskScheduleBuilder.list(task1).get(0).getScheduleName()).isEqualTo(taskSchedule1.getScheduleName());
            assertThat(taskScheduleBuilder.list(task1).get(0).getScheduleProperties().containsKey(SchedulerPropertyKeys.CRON_EXPRESSION)).isTrue();
            assertThat(taskScheduleBuilder.list(task1).get(0).getScheduleProperties().get(SchedulerPropertyKeys.CRON_EXPRESSION)).isEqualTo(DEFAULT_CRON_EXPRESSION);

            assertThat(taskScheduleBuilder.list(task2).get(0).getScheduleName()).isEqualTo(taskSchedule2.getScheduleName());
            assertThat(taskScheduleBuilder.list(task2).get(0).getScheduleProperties().containsKey(SchedulerPropertyKeys.CRON_EXPRESSION)).isTrue();
            assertThat(taskScheduleBuilder.list(task2).get(0).getScheduleProperties().get(SchedulerPropertyKeys.CRON_EXPRESSION)).isEqualTo(DEFAULT_CRON_EXPRESSION);
        }
    }

    @Test
    @Tag("group4")
    public void scheduleLifeCycle() {
        logger.info("schedule-lifecycle-test");

        try (Task task = Task.builder(dataFlowOperations).name(randomName("task")).definition("testtimestamp").build();
             TaskSchedule taskSchedule = TaskSchedule.builder(dataFlowOperations).scheduleName(randomName("schedule")).task(task).build()) {

            assertThat(taskSchedule.isScheduled()).isFalse();

            logger.info("schedule-lifecycle-test: SCHEDULE");
            taskSchedule.schedule(DEFAULT_CRON_EXPRESSION, Collections.emptyMap());

            assertThat(taskSchedule.isScheduled()).isTrue();

            TaskSchedule retrievedSchedule = TaskSchedule.builder(dataFlowOperations).findByScheduleName(taskSchedule.getScheduleName()).get();
            assertThat(retrievedSchedule.getScheduleName()).isEqualTo(taskSchedule.getScheduleName());
            assertThat(retrievedSchedule.getScheduleProperties().containsKey(SchedulerPropertyKeys.CRON_EXPRESSION)).isTrue();
            assertThat(retrievedSchedule.getScheduleProperties().get(SchedulerPropertyKeys.CRON_EXPRESSION)).isEqualTo(DEFAULT_CRON_EXPRESSION);

            logger.info("schedule-lifecycle-test: UNSCHEDULE");
            taskSchedule.unschedule();

            assertThat(taskSchedule.isScheduled()).isFalse();
        }
    }

    private static String randomName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 10);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(SchedulerEnabledCondition.class)
    public @interface AssumeSchedulerEnabled {
    }

    static class SchedulerEnabledCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
            DataFlowTemplate dataFlowTemplate = SpringExtension.getApplicationContext(extensionContext).getBean(DataFlowTemplate.class);
            boolean schedulerEnabled = dataFlowTemplate.aboutOperation().get().getFeatureInfo().isSchedulesEnabled();

            if (!schedulerEnabled) {
                logger.info("Scheduler feature is not enabled. Skipping test!");
                return ConditionEvaluationResult.disabled("Scheduler feature is not enabled. Skipping test!");
            }

            return ConditionEvaluationResult.enabled("Scheduler feature is enabled. Continuing test!");
        }
    }
}
