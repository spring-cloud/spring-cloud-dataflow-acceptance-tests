/*
 * Copyright 2017-2021 the original author or authors.
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.acceptance.test.util.DataFlowTemplateBuilder;
import org.springframework.cloud.dataflow.acceptance.test.util.LogTestNameExtension;
import org.springframework.cloud.dataflow.acceptance.test.util.RestTemplateConfigurer;
import org.springframework.cloud.dataflow.acceptance.test.util.TestConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.AppRegistryOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.JobOperations;
import org.springframework.cloud.dataflow.rest.client.SchedulerOperations;
import org.springframework.cloud.dataflow.rest.client.TaskOperations;
import org.springframework.cloud.dataflow.rest.resource.JobExecutionResource;
import org.springframework.cloud.dataflow.rest.resource.ScheduleInfoResource;
import org.springframework.cloud.dataflow.rest.resource.TaskDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.hateoas.PagedModel;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class that is used by task acceptance tests. This class contains commonly
 * used utility methods for task acceptance tests as well as the ability to retrieve
 * results from task repository.
 *
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author David Turanski
 */
@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties({ TestConfigurationProperties.class })
@ExtendWith(LogTestNameExtension.class)
public abstract class AbstractTaskTests implements InitializingBean {

	protected static final String DEFAULT_CRON_EXPRESSION_KEY = "spring.cloud.scheduler.cron.expression";

	protected final Logger logger = LoggerFactory.getLogger(this.getClass());

	protected boolean tasksRegistered = false;

	protected RestTemplate restTemplate;

	protected TaskOperations taskOperations;

	protected JobOperations jobOperations;

	protected SchedulerOperations schedulerOperations;

	protected AppRegistryOperations appRegistryOperations;

	@Autowired
	TestConfigurationProperties configurationProperties;

	protected List<String> composedTasksToBeDestroyed;

	@BeforeEach
	public void setup() {
		composedTasksToBeDestroyed = new ArrayList<>();
		registerTasks();
	}

	@AfterEach
	public void teardown() {
		if (schedulerOperations != null) {
			PagedModel<ScheduleInfoResource> scheduleInfoResources = schedulerOperations.list();
			Iterator<ScheduleInfoResource> scheduleInfoResourceIterator = scheduleInfoResources.iterator();
			ScheduleInfoResource scheduleInfoResource;

			while (scheduleInfoResourceIterator.hasNext()) {
				scheduleInfoResource = scheduleInfoResourceIterator.next();
				schedulerOperations.unschedule(scheduleInfoResource.getScheduleName());
			}
		}

		// Clean up composed tasks independently, because they have their own cleanup cycle.
		if (composedTasksToBeDestroyed.size() > 0) {
			for (String taskName : composedTasksToBeDestroyed) {
				this.taskOperations.destroy(taskName);
			}
		}
		else {
			PagedModel<TaskDefinitionResource> taskExecutionResources = taskOperations.list();
			Iterator<TaskDefinitionResource> taskDefinitionResourceIterator = taskExecutionResources.iterator();
			TaskDefinitionResource taskDefinitionResource;

			while (taskDefinitionResourceIterator.hasNext()) {
				taskDefinitionResource = taskDefinitionResourceIterator.next();
				taskOperations.destroy(taskDefinitionResource.getName());
			}
		}

		try {
			cleanUpExecutions();
		} catch (Exception e) {
			logger.error("Error cleaning up task executions", e);
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
	 * Creates a unique composed task definition name from a UUID and launches the task based
	 * on the definition specified.
	 *
	 * @param definition The composed task definition to test;
	 * @return The name of the task associated with this launch.
	 */
	protected String composedTaskLaunch(String definition) {
		return composedTaskLaunch(definition, Collections.EMPTY_MAP,
				Collections.EMPTY_LIST);
	}

	/**
	 * Creates a unique composed task definition name from a UUID and launches the task based
	 * on the definition specified.
	 *
	 * @param definition The composed task definition to test;
	 * @return The name of the task associated with this launch.
	 */
	protected String composedTaskLaunch(String definition, Map<String, String> properties,
			List<String> arguments) {
	    arguments = new ArrayList<>(arguments);
        arguments.add("--interval-time-between-checks=1000");
		String taskName = taskLaunch(definition, properties,
				arguments);
		composedTasksToBeDestroyed.add(taskName);
		return taskName;
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

		String taskDefinitionName = randomTaskName();
		taskOperations.create(taskDefinitionName, definition, "Sample Definition for " + taskDefinitionName);
		taskOperations.launch(taskDefinitionName, properties, arguments, null);
		return taskDefinitionName;
	}

	/**
	 * Creates a unique task definition name from a UUID.
	 *
	 * @param definition The definition to test.
	 * @return The name of the task associated with this launch.
	 */
	protected String taskCreate(String definition) {
		String taskDefinitionName = randomTaskName();
		taskOperations.create(taskDefinitionName, definition, "Sample Definition for " + taskDefinitionName);
		return taskDefinitionName;
	}

	private String randomTaskName() {
		return "task-" + UUID.randomUUID().toString().substring(0, 10);
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
		taskOperations.launch(taskDefinitionName, properties, arguments, null);
	}

	/**
	 * Imports the proper apps required for the acceptance tests.
	 */
	protected void registerTasks() {
		if (this.tasksRegistered) {
			return;
		}
		logger.info(String.format("Importing task apps from uri resource: %s",
				configurationProperties.getTaskRegistrationResource()));
		appRegistryOperations.importFromResource(configurationProperties.getTaskRegistrationResource(), true);

		logger.info(String.format("Importing task apps from properties: %s",
				configurationProperties.getTaskRegistrationProperties(), ","));
		appRegistryOperations.registerAll(configurationProperties.getTaskRegistrationProperties(), true);

		logger.info("Done importing task apps.");
		this.tasksRegistered = true;
	}

	/**
	 * Creates the task and app operations that will be used for the acceptance test.
	 */
	public void afterPropertiesSet() {
		restTemplate = new RestTemplateConfigurer().skipSslValidation(true).configure();
		DataFlowTemplate dataFlowOperationsTemplate = DataFlowTemplateBuilder
						.serverUri(configurationProperties.getServerUri()).restTemplate(restTemplate).build();
		taskOperations = dataFlowOperationsTemplate.taskOperations();
		schedulerOperations = dataFlowOperationsTemplate.schedulerOperations();
		appRegistryOperations = dataFlowOperationsTemplate.appRegistryOperations();
		jobOperations = dataFlowOperationsTemplate.jobOperations();
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
		boolean isComplete = isTaskComplete(taskDefinitionName, taskExecutionCount);
		logger.info("Waiting for task: {}", taskDefinitionName);
		while (!isComplete && System.currentTimeMillis() < timeout) {
			try {
				Thread.sleep(configurationProperties.getDeployPauseTime() * 1000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}

			isComplete =  isTaskComplete(taskDefinitionName,  taskExecutionCount);
		}
		return isComplete;
	}

	private  boolean isTaskComplete(String taskDefinitionName, int taskExecutionCount) {
		boolean isComplete = false;
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
			if (taskExecutionResource.getTaskName() != null) {
				if (taskExecutionResourceTaskNameMatcher(taskDefinitionName).test(taskExecutionResource)) {
					result.add(taskExecutionResource);
				}
			}
		}
		return result;
	}

	protected Predicate<TaskExecutionResource> taskExecutionResourceTaskNameMatcher(String taskName) {
		return r -> r.getTaskName().equals(taskName);
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
		for (ScheduleInfoResource resource : this.schedulerOperations.list()) {
			if (resource.getScheduleName().equals(scheduleName)) {
				result = true;
				break;
			}
		}
		return result;
	}

	/**
	 * Asserts that the {@link ScheduleInfoResource} contains the expected data.
	 * @param scheduleInfoResource the {@link ScheduleInfoResource} that needs to be
	 *     interrogated.
	 * @param scheduleName The expected name of the schedule.
	 * @param cronExpression The expected expression.
	 */
	protected void verifyScheduleIsValid(ScheduleInfoResource scheduleInfoResource, String scheduleName,
			String cronExpression) {
		assertThat(scheduleInfoResource.getScheduleName()).isEqualTo(scheduleName);
		assertThat(scheduleInfoResource.getScheduleProperties().containsKey(DEFAULT_CRON_EXPRESSION_KEY)).isTrue();
		assertThat(scheduleInfoResource.getScheduleProperties().get(DEFAULT_CRON_EXPRESSION_KEY))
				.isEqualTo(cronExpression);
	}

	/**
	 * Retrieves a {@link org.springframework.hateoas.PagedModel} of the existing schedules.
	 */
	protected PagedModel<ScheduleInfoResource> listSchedules() {
		return this.schedulerOperations.list();
	}

	/**
	 * Retrieves a {@link PagedModel} of the existing {@link ScheduleInfoResource}s that
	 * are associated with the task definition name.
	 * @param taskDefinitionName The name of the task definition that the schedules should be
	 *     associated.
	 */
	protected PagedModel<ScheduleInfoResource> listSchedules(String taskDefinitionName) {
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

	protected Collection<JobExecutionResource> getJobExecutionByTaskName(String taskName) {
		PagedModel<JobExecutionResource> jobExecutionPagedResources = this.jobOperations
				.executionListByJobName(taskName);
		return jobExecutionPagedResources.getContent();
	}

	/**
	 * Restart a failed Job.
	 * @param jobExecutionId the job execution id assocated with the job to be restarted.
	 */
	protected void  restartJob(long jobExecutionId) {
		this.jobOperations.executionRestart(jobExecutionId);
	}

	private void cleanUpExecutions() {
		Collection<TaskExecutionResource> taskExecutionResources  = taskOperations.executionList().getContent();
		List<Long> parentIds = taskExecutionResources.stream()
				.filter(taskExecutionResource -> StringUtils.isEmpty(taskExecutionResource.getParentExecutionId()))
				.map(taskExecution-> {
					logger.info("deleting parent task execution task name: {} executionId: {} exit code: {} exit msg: {}",
							taskExecution.getTaskName(),
							taskExecution.getExecutionId(),
							taskExecution.getExitCode(),
							taskExecution.getErrorMessage());
					return taskExecution;
				})
				.map(TaskExecutionResource::getExecutionId)
				.collect(Collectors.toList());

		cleanUpAndRemoveDataForTaskExecutions(parentIds);
		//Clean up any remaining
		cleanUpAndRemoveDataForTaskExecutions(taskOperations.executionList().getContent()
				.stream().map(taskExecution-> {
							logger.info("deleting task execution task name: {} executionId: {} exit code: {} exit msg: {}",
									taskExecution.getTaskName(),
									taskExecution.getExecutionId(),
									taskExecution.getExitCode(),
									taskExecution.getErrorMessage());
							return taskExecution;
						})
						.map(TaskExecutionResource::getExecutionId).collect(Collectors.toList()));
	}

	private void cleanUpAndRemoveDataForTaskExecutions(List<Long> ids) {
		if (CollectionUtils.isEmpty(ids)) {
			return;
		}
		URI uri = new DefaultUriBuilderFactory(configurationProperties.getServerUri())
				.builder()
				.pathSegment("tasks","executions")
				.pathSegment(StringUtils.collectionToCommaDelimitedString(ids))
				.queryParam("action","CLEANUP,REMOVE_DATA")
				.build();
		logger.info("cleaning up task executions data for ids {}", StringUtils.collectionToCommaDelimitedString(ids));
		restTemplate.delete(uri);
	}


}
