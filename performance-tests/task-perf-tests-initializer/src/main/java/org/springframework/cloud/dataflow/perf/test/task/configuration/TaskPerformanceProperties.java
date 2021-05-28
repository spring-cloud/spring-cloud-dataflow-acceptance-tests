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

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "org.springframework.cloud.dataflow.task.performance")
public class TaskPerformanceProperties {

	/**
	 * The prefix to be used for task definition names.
	 */
	private String taskPrefix = "perfTestTask";

	/**
	 * Then number of definitions to create.
	 */
	private Integer taskDefinitionCount = 10;

	/**
	 * If true task executions will be created by launching tasks.
	 */
	private Boolean addTaskExecutions = true;

	/**
	 * Removes all task definitions with a task name starting with the prefix and their associated executions.
	 */
	private Boolean isCleanup = false;

	/**
	 * The number of task executions per task definition to be inserted into the task execution table.
	 */
	private Integer taskExecutionCount = 10;

    /**
     * The number of task Job instnaces per task execution to be inserted into the batch infra. Each job instance
     * will have 1 job execution with a single step.   Default is 0.
     */
    private Integer jobInstancesPerTaskExecution = 0;

	public String getTaskPrefix() {
		return taskPrefix;
	}

	public void setTaskPrefix(String taskPrefix) {
		this.taskPrefix = taskPrefix;
	}

	public Integer getTaskDefinitionCount() {
		return taskDefinitionCount;
	}

	public void setTaskDefinitionCount(Integer taskDefinitionCount) {
		this.taskDefinitionCount = taskDefinitionCount;
	}

	public Boolean getLaunchTasks() {
		return addTaskExecutions;
	}

	public void setLaunchTasks(Boolean launchTasks) {
		addTaskExecutions = launchTasks;
	}

	public Integer getTaskExecutionCount() {
		return taskExecutionCount;
	}

	public void setTaskExecutionCount(Integer taskExecutionCount) {
		this.taskExecutionCount = taskExecutionCount;
	}

	public Boolean getAddTaskExecutions() {
		return addTaskExecutions;
	}

	public void setAddTaskExecutions(Boolean addTaskExecutions) {
		this.addTaskExecutions = addTaskExecutions;
	}

	public Boolean getCleanup() {
		return isCleanup;
	}

	public void setCleanup(Boolean cleanup) {
		isCleanup = cleanup;
	}

    public Integer getJobInstancesPerTaskExecution() {
        return jobInstancesPerTaskExecution;
    }

    public void setJobInstancesPerTaskExecution(Integer jobInstancesPerTaskExecution) {
        this.jobInstancesPerTaskExecution = jobInstancesPerTaskExecution;
    }
}
