/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.dataflow.perf.test.task.configuration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.item.database.support.DataFieldMaxValueIncrementerFactory;
import org.springframework.batch.item.database.support.DefaultDataFieldMaxValueIncrementerFactory;
import org.springframework.cloud.dataflow.rest.client.DataFlowClientException;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.TaskOperations;
import org.springframework.cloud.dataflow.rest.client.dsl.task.Task;
import org.springframework.cloud.dataflow.rest.client.dsl.task.TaskBuilder;
import org.springframework.cloud.task.repository.support.DatabaseType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;

public class TaskUtils {

	private static final Logger logger = LoggerFactory.getLogger(TaskUtils.class);

	/** Create a specified number of task definitions using the taskNamePrefix.
	 * If a task definition already exists then the app will skip that entry and continue creating the rest of the task definitions.
	 *
	 * @param taskNamePrefix The task name prefix to use for each of the task definitions.
	 * @param numberOfTaskDefinitions The number of task definitions to create.
	 * @param dataFlowOperations The dataflowOperations instance used to connect to dataflow.
	 */
	public static void createTaskDefinitions(String taskNamePrefix, int numberOfTaskDefinitions, DataFlowOperations dataFlowOperations) {
		logger.info(String.format("Creating %s task definitions", numberOfTaskDefinitions));

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);

		String ctrTaskDefinition = "timestamp";
		final List<Task> taskDefinitions = new ArrayList<>();
		for (int i = 0; i < numberOfTaskDefinitions; i++) {
			try {
				taskDefinitions.add(taskBuilder
						.name(taskNamePrefix + "-" + i)
						.definition(ctrTaskDefinition)
						.description("Task Definition created for perf test")
						.build());
			}
			catch (DataFlowClientException dfce) {
				if (dfce.getMessage().contains("because another one has already been registered"))
					;
				{
					logger.info(dfce.getMessage());
				}
			}
		}
	}


	/**
	 * Retrieve a list of task definitions whose name starts with the  prefix.
	 * @param taskNamePrefix The task name prefix used to retrieve task defintions.
	 * @param dataFlowOperations The dataflowOperations instance used to connect to dataflow.
	 * @return
	 */
	public static List<Task> getTaskDefinitionsByPrefix(String taskNamePrefix, DataFlowOperations dataFlowOperations) {
		return Task.builder(dataFlowOperations).allTasks().stream().
				filter(task -> task.getTaskName().startsWith(taskNamePrefix)).
				collect(Collectors.toList());
	}

	/**
	 * Simple impl for launching the specified number tasks executions.
	 * @param numberOfLaunches Number of launches
	 * @param taskDefinitions List of task definitions to be used for task launches.
	 */
	public static void launchTasks(int numberOfLaunches, List<Task> taskDefinitions) {
		int numberOfDefinitions = taskDefinitions.size();
		for (int i = 0; i < numberOfLaunches; i++) {
			taskDefinitions.get(i % numberOfDefinitions).launch();
		}
	}


	/**
	 * Inserts the specified number of task executions into the task_execution table.
	 * @param numberOfTaskExecutions Number of task executions to insert.
	 * @param taskDefinitions A list of task definitions to use for populating task the task name and task description.
	 * @param dataSource The dataSource to use for inserting the data.
	 */
	public static void dbInsertTaskExecutions(int numberOfTaskExecutions, List<Task> taskDefinitions, DataSource dataSource) {
		logger.info(String.format("Creating %s task executions", numberOfTaskExecutions));
		DataFieldMaxValueIncrementer incrementer = getIncrementer(dataSource);
		int sizeOfTaskDefinitions = taskDefinitions.size();
		for (int i = 0; i < numberOfTaskExecutions; i++) {
			long executionid = incrementer.nextLongValue();
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			jdbcTemplate.update("INSERT INTO TASK_EXECUTION (TASK_EXECUTION_ID," +
							"START_TIME,END_TIME,TASK_NAME,EXIT_CODE,EXIT_MESSAGE,ERROR_MESSAGE," +
							"LAST_UPDATED,EXTERNAL_EXECUTION_ID,PARENT_EXECUTION_ID) VALUES " +
							"( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					executionid, new Date(), new Date(), taskDefinitions.get(i % sizeOfTaskDefinitions).getTaskName(),
					0, null, null, new Date(), taskDefinitions.get(i % sizeOfTaskDefinitions).getTaskName(), null);
		}
	}

	/**
	 * Removes all task definitions that has a task name that starts with the taskName prefix and their associated task executions.
	 * @param taskNamePrefix the prefix to determine if a task definition should be deleted.
	 * @param dataFlowOperations The dataflowOperations instance used to connect to dataflow.
	 */
	public static void cleanup(String taskNamePrefix, DataFlowOperations dataFlowOperations) {
		List<Task> taskDefinitions = getTaskDefinitionsByPrefix(taskNamePrefix, dataFlowOperations);
		TaskOperations taskOperations = dataFlowOperations.taskOperations();
		taskDefinitions.stream().forEach(task -> taskOperations.destroy(task.getTaskName(), true));
	}

	/**
	 * Creates a incrementer for the DataSource.
	 *
	 * @param dataSource the datasource that the incrementer will use to record current id.
	 * @return a DataFieldMaxValueIncrementer object.
	 */
	private static DataFieldMaxValueIncrementer getIncrementer(DataSource dataSource) {
		DataFieldMaxValueIncrementerFactory incrementerFactory = new DefaultDataFieldMaxValueIncrementerFactory(
				dataSource);
		String databaseType = null;
		try {
			databaseType = DatabaseType.fromMetaData(dataSource).name();
		}
		catch (MetaDataAccessException e) {
			throw new IllegalStateException(e);
		}
		return incrementerFactory.getIncrementer(databaseType, "TASK_SEQ");
	}
}
