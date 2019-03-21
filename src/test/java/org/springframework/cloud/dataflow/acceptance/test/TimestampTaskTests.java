/*
 * Copyright 2017 the original author or authors.
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

import java.util.List;

import org.junit.Test;

import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the timestamp task.
 * @author Glenn Renfro
 * @author Thomas Risberg
 */
public class TimestampTaskTests extends AbstractTaskTests {

	@Test
	public void timeStampTests() {
		String taskDefinitionName = taskLaunch("timestamp");
		assertTaskExecutions(taskDefinitionName, 0, 1);
	}

	@Test
	public void timeStampTestsMultipleLaunch() {
		String taskDefinitionName = taskLaunch("timestamp");
		assertTaskExecutions(taskDefinitionName, 0, 1);
		launchExistingTask(taskDefinitionName);
		assertTaskExecutions(taskDefinitionName, 0, 2);
	}

	@Test
	public void taskNoLaunch() {
		taskOperations.create("task-foobar", "timestamp");
		waitForTaskToComplete("task-foobar", 1); // this should time out
		assertTrue(waitForTaskToComplete("task-foobar", 0));
	}

	private void assertTaskExecutions(String taskDefinitionName,
			int expectedExitCode, int expectedCount) {
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertTrue(waitForTaskToComplete(taskDefinitionName, expectedCount));

		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			assertEquals(expectedExitCode, taskExecutionResource.getExitCode());
		}
	}

}
