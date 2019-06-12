/*
 * Copyright 2019 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the composed tasks.
 * @author Glenn Renfro
 */
public class ComposedTaskTests extends AbstractTaskTests {

	@Test
	public void ctrLaunch() {
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp");
		assertTaskExecutions(taskDefinitionName, 0, 1, 1);
	}

	@Test
	public void ctrMultipleLaunch() {
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp");
		assertTaskExecutions(taskDefinitionName, 0, 1, 1);
		launchExistingTask(taskDefinitionName);
		assertParentTaskExecution(taskDefinitionName, 0, 2, 1);
	}

	@Test
	public void ctrMultipleLaunchWithProperties() {
		Map<String, String> properties = new HashMap<>();
		properties.put("app.composed-task-runner.increment-instance-enabled", "true");
		properties.put("app.composed-task-runner.interval-time-between-checks", "1");
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp", properties, Collections.EMPTY_LIST);
		assertTaskExecutions(taskDefinitionName, 0, 1, 1);
		launchExistingTask(taskDefinitionName, properties, Collections.EMPTY_LIST);
		assertParentTaskExecution(taskDefinitionName, 0, 2, 2);
	}

	private void assertTaskExecutions(String taskDefinitionName,
			int expectedExitCode, int expectedCount, int expectJobCount) {
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertTrue(waitForTaskToComplete(taskDefinitionName, expectedCount));
		assertTrue(waitForTaskToComplete(taskDefinitionName + "-a", expectedCount));
		assertTrue(waitForTaskToComplete(taskDefinitionName + "-b", expectedCount));

		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			assertEquals(expectedExitCode, taskExecutionResource.getExitCode());
		}
	}

	private void assertParentTaskExecution(String taskDefinitionName,
			int expectedExitCode, int expectedCount, int expectJobCount) {
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertTrue(waitForTaskToComplete(taskDefinitionName, expectedCount));

		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			assertEquals(expectedExitCode, taskExecutionResource.getExitCode());
		}
		assertEquals(expectJobCount, getJobExecutionByTaskName(taskDefinitionName).size());
	}
}
