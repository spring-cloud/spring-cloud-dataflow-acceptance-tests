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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the composed tasks.
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 */
public class ComposedTaskTests extends AbstractTaskTests {

	@Test
	public void ctrLaunch() {
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp");
		assertTaskExecutions(taskDefinitionName, 0, 1);
	}

	@Test
	public void ctrMultipleLaunchShouldFailIfBatchParametersDoNotChange() {
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp");
		assertTaskExecutions(taskDefinitionName, 0, 1);
		launchExistingTask(taskDefinitionName);
		assertLastParentTaskExecution(taskDefinitionName, 1);
	}

	@Test
	public void ctrMultipleLaunchWithArguments() {
		List<String> arguments = new ArrayList<>();
        arguments.add("--increment-instance-enabled=true");
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp", Collections.EMPTY_MAP, arguments);
		assertTaskExecutions(taskDefinitionName, 0, 1);
		launchExistingTask(taskDefinitionName, Collections.EMPTY_MAP, arguments);
		assertParentTaskExecution(taskDefinitionName, 0, 2, 2);
	}

	private void assertTaskExecutions(String taskDefinitionName,
			int expectedExitCode, int expectedCount) {

		assertTrue(waitForTaskToComplete(taskDefinitionName, expectedCount));
		assertTrue(waitForTaskToComplete(taskDefinitionName + "-a", expectedCount));
		assertTrue(waitForTaskToComplete(taskDefinitionName + "-b", expectedCount));
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);

		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			assertEquals(expectedExitCode, (long) taskExecutionResource.getExitCode());
		}
	}

	private void assertParentTaskExecution(String taskDefinitionName,
			int expectedExitCode, int expectedCount, int expectJobCount) {

		assertTrue(waitForTaskToComplete(taskDefinitionName, expectedCount));
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			assertEquals(expectedExitCode, (long) taskExecutionResource.getExitCode());
		}
		assertEquals(expectJobCount, getJobExecutionByTaskName(taskDefinitionName).size());
	}

	private void assertLastParentTaskExecution(String taskDefinitionName, int expectedExitCode) {
		assertTrue(waitForTaskToComplete(taskDefinitionName, 1));
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);

		TaskExecutionResource lastExecution = taskExecutionResources.get(0);

		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			if (taskExecutionResource.getEndTime().compareTo(lastExecution.getEndTime()) > 0) {
				lastExecution = taskExecutionResource;
			}
		}
		assertEquals(expectedExitCode, (long) lastExecution.getExitCode());
	}
}
