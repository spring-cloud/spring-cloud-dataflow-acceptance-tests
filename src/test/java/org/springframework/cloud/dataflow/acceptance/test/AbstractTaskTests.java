/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.acceptance.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.acceptance.test.util.LogTestNameRule;
import org.springframework.cloud.dataflow.acceptance.test.util.TestConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.AppRegistryOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.SchedulerOperations;
import org.springframework.cloud.dataflow.rest.client.TaskOperations;
import org.springframework.cloud.dataflow.rest.resource.ScheduleInfoResource;
import org.springframework.cloud.dataflow.rest.resource.TaskDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.hateoas.PagedResources;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class that is used by task acceptance tests. This class contains commonly
 * used utility methods for task acceptance tests as well as the ability to retrieve
 * results from task repository.
 *
 * @author Glenn Renfro
 * @author Thomas Risberg
 */
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(TestConfigurationProperties.class)
public abstract class AbstractTaskTests implements InitializingBean {

	protected static final String DEFAULT_CRON_EXPRESSION_KEY = "spring.cloud.scheduler.cron.expression";

	private static final Logger logger = LoggerFactory.getLogger(AbstractTaskTests.class);

	private static boolean tasksRegistered = false;

	@Rule
	public LogTestNameRule logTestName = new LogTestNameRule();

	protected RestTemplate restTemplate;

	protected TaskOperations taskOperations;

	protected SchedulerOperations schedulerOperations;

	protected AppRegistryOperations appRegistryOperations;

	@Autowired
	TestConfigurationProperties configurationProperties;

	@Before
	public void setup() {
		if (tasksRegistered) {
			return;
		}
		registerTasks();
	}

	@After
	public void teardown() {
		PagedResources<TaskDefinitionResource> taskExecutionResources = taskOperations.list();
		Iterator<TaskDefinitionResource> taskDefinitionResourceIterator = taskExecutionResources.iterator();
		TaskDefinitionResource taskDefinitionResource = null;
		while (taskDefinitionResourceIterator.hasNext()) {
			taskDefinitionResource = taskDefinitionResourceIterator.next();
			taskOperations.destroy(taskDefinitionResource.getName());
		}

	}

	/**
	 * Creates a unique task definition name from a UUID and launches the task based on the
	 * definition specified.
	 *
	 * @param definition The definition to test;
	 * @return The name of the task associated with this launch.
	 */
	protected String taskLaunch(String definition) {
		return taskLaunch(definition, Collections.EMPTY_MAP,
				Collections.EMPTY_LIST);
	}

	/**
	 * Creates a unique task definition name from a UUID and launches the task based on the
	 * definition specified.
	 *
	 * @param definition The definition to test.
	 * @param properties Map containing deployment properties for the task.
	 * @param arguments List containing the arguments used to execute the task.
	 * @return The name of the task associated with this launch.
	 */
	protected String taskLaunch(String definition,
			Map<String, String> properties, List<String> arguments) {
		String taskDefinitionName = "task-" + UUID.randomUUID().toString();
		taskOperations.create(taskDefinitionName, definition);
		taskOperations.launch(taskDefinitionName, properties, arguments);
		return taskDefinitionName;
	}

	/**
	 * Creates a unique task definition name from a UUID.
	 *
	 * @param definition The definition to test.
	 * @return The name of the task associated with this launch.
	 */
	protected String taskCreate(String definition) {
		String taskDefinitionName = "task-" + UUID.randomUUID().toString();
		taskOperations.create(taskDefinitionName, definition);
		return taskDefinitionName;
	}

	/**
	 * Launch an existing task definition.
	 */
	protected void launchExistingTask(String taskDefinitionName) {
		launchExistingTask(taskDefinitionName, Collections.EMPTY_MAP,
				Collections.EMPTY_LIST);
	}

	/**
	 * Launch an existing task definition.
	 * @param taskDefinitionName the name of the definition to relaunch
	 * @param properties Map containing deployemrnt properties for the task.
	 * @param arguments List containing the arguments used to execute the task.
	 */
	protected void launchExistingTask(String taskDefinitionName, Map<String, String> properties,
			List<String> arguments) {
		taskOperations.launch(taskDefinitionName, properties, arguments);
	}

	/**
	 * Imports the proper apps required for the acceptance tests.
	 */
	protected void registerTasks() {
		logger.info(String.format("Importing task apps from uri resource: %s",
				configurationProperties.getTaskRegistrationResource()));
		appRegistryOperations.importFromResource(configurationProperties.getTaskRegistrationResource(), true);
		logger.info("Done importing task apps.");
		this.tasksRegistered = true;
	}

	/**
	 * Creates the task and app operations that will be used for the acceptance test.
	 */
	public void afterPropertiesSet() {
		if (restTemplate == null) {
			try {
				DataFlowTemplate dataFlowOperationsTemplate = new DataFlowTemplate(
						new URI(configurationProperties.getServerUri()));
				taskOperations = dataFlowOperationsTemplate.taskOperations();
				schedulerOperations = dataFlowOperationsTemplate.schedulerOperations();
				appRegistryOperations = dataFlowOperationsTemplate.appRegistryOperations();
			}
			catch (URISyntaxException uriException) {
				throw new IllegalStateException(uriException);
			}
			restTemplate = new RestTemplate();
		}
	}

	/**
	 * Waits the specified period of time for all task executions to complete for a specific
	 * task name.
	 *
	 * @param taskDefinitionName the task name to monitor for in task execution list result.
	 * @param taskExecutionCount the number of expected task executions.
	 * @return true if they are complete else false.
	 */
	protected boolean waitForTaskToComplete(String taskDefinitionName, int taskExecutionCount) {
		long timeout = System.currentTimeMillis() + (configurationProperties.getMaxWaitTime() * 1000);
		boolean isComplete = false;
		while (!isComplete && System.currentTimeMillis() < timeout) {
			try {
				Thread.sleep(configurationProperties.getDeployPauseTime() * 1000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}

			List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
			if (taskExecutionResources.size() >= taskExecutionCount) {
				isComplete = true;
				for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
					isComplete = (taskExecutionResource != null && taskExecutionResource.getEndTime() != null);
					if (!isComplete) {
						break;
					}
				}
			}
		}
		return isComplete;
	}

	/**
	 * Retrieves a list of TaskExecutionResources for a specific task.
	 *
	 * @param taskDefinitionName The name of the task to query
	 * @return list containing the TaskExecutionResources that matched the task name.
	 */
	protected List<TaskExecutionResource> getTaskExecutionResource(
			String taskDefinitionName) {
		Iterator<TaskExecutionResource> taskExecutionIterator = taskOperations.executionList().iterator();
		TaskExecutionResource taskExecutionResource;
		List<TaskExecutionResource> result = new ArrayList<>();

		while (taskExecutionIterator.hasNext()) {
			taskExecutionResource = taskExecutionIterator.next();
			if (taskExecutionResource.getTaskName().equals(taskDefinitionName)) {
				result.add(taskExecutionResource);
			}
		}
		return result;
	}

	/**
	 * Creates a unique schedule name from a UUID from an existing task definition.
	 *
	 * @param taskDefinitionName The definition to test.
	 * @param properties Map containing deployment properties for the task.
	 * @param arguments List containing the arguments used to execute the task.
	 * @return The name of the schedule.
	 */
	protected String schedule(String taskDefinitionName,
			Map<String, String> properties, List<String> arguments) {
		String scheduleName = "schedule-" + UUID.randomUUID().toString();
		this.schedulerOperations.schedule(scheduleName, taskDefinitionName, properties, arguments);
		return scheduleName;
	}

	/**
	 * Verifies that the scheduleName specified exists in the schedule list results.
	 * @param scheduleName the name of the schedule to search.
	 * @return true if found else false;
	 */

	protected boolean verifyScheduleExists(String scheduleName) {
		boolean result = false;
		for(ScheduleInfoResource resource : this.schedulerOperations.list()) {
			if(resource.getScheduleName().equals(scheduleName)){
				result = true;
				break;
			}
		}
		return result;
	}

	/**
	 * Asserts that the {@link ScheduleInfoResource} contains the expected data.
	 * @param scheduleInfoResource the {@link ScheduleInfoResource} that needs to be interrogated.
	 * @param scheduleName The expected name of the schedule.
	 * @param cronExpression The expected expression.
	 */
	protected void verifyScheduleIsValid(ScheduleInfoResource scheduleInfoResource, String scheduleName, String cronExpression) {
		assertThat(scheduleInfoResource.getScheduleName()).isEqualTo(scheduleName);
		assertThat(scheduleInfoResource.getScheduleProperties().containsKey(DEFAULT_CRON_EXPRESSION_KEY)).isTrue();
		assertThat(scheduleInfoResource.getScheduleProperties().get(DEFAULT_CRON_EXPRESSION_KEY)).isEqualTo(cronExpression);
	}

	/**
	 * Retrieves a {@link PagedResources} of the existing schedules.
	 */
	protected PagedResources<ScheduleInfoResource> listSchedules() {
		return this.schedulerOperations.list();
	}

	/**
	 * Retrieves a {@link PagedResources} of the existing {@link ScheduleInfoResource}s that are
	 * associated with the task definition name.
	 * @param taskDefinitionName The name of the task definition that the schedules should be associated.
	 */
	protected PagedResources<ScheduleInfoResource> listSchedules(String taskDefinitionName) {
		return this.schedulerOperations.list(taskDefinitionName);
	}

	/**
	 * Deletes an existing schedule based on the schedule name.
	 *
	 * @param scheduleName the name of the schedule instance to be unscheduled.
	 */
	protected void unschedule(String scheduleName) {
		this.schedulerOperations.unschedule(scheduleName);
	}

	public enum TaskTestTypes {
		TIMESTAMP,
		CORE
	}

}
