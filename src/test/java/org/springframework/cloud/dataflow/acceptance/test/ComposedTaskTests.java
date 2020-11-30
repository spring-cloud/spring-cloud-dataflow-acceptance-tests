/*
 * Copyright 2019-2020 the original author or authors.
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

import org.junit.Ignore;
import org.junit.Test;

import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the composed tasks.
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 * @author David Turanski
 */
public class ComposedTaskTests extends AbstractTaskTests {

	@Test
	public void ctrLaunch() {
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp");
		assertTaskExecutions(taskDefinitionName, 0, 1);
	}

	@Ignore("This behavior is currently dependent on SCDF version")
	@Test
	public void ctrMultipleLaunchBatchParametersDoNotChange() {
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp");
		logger.info("Launched composed task: {}", taskDefinitionName);
		assertTaskExecutions(taskDefinitionName, 0, 1);
		launchExistingTask(taskDefinitionName);
		assertLastParentTaskExecution(taskDefinitionName, 0);
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

	@Test
	public void ctrFailedGraph() {
		// testFailedGraph COMPLETE
		// testFailedGraph-scenario ERROR
		// testFailedGraph-timestamp UNKNOWN
		//
		// testFailedGraph exit 0
		// testFailedGraph-scenario exit 1
		String taskDefinitionName = composedTaskLaunch("scenario --fail-task=true && timestamp");
		assertTrue(waitForTaskToComplete(taskDefinitionName + "-scenario", 1));
		assertTrue(waitForTaskToComplete(taskDefinitionName, 1));

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			logger.info("task name: {} end time: {} exit code: {}",
					taskExecutionResource.getTaskName(),
					taskExecutionResource.getEndTime(),
					taskExecutionResource.getExitCode());
		}
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		taskExecutionResources = getTaskExecutionResource(taskDefinitionName + "-scenario");
		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			logger.info("task name: {} end time: {} exit code: {}",
					taskExecutionResource.getTaskName(),
					taskExecutionResource.getEndTime(),
					taskExecutionResource.getExitCode());
		}
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(1);

		taskExecutionResources = getTaskExecutionResource(taskDefinitionName + "-timestamp");
		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			logger.info("task name: {} end time: {} exit code: {}",
					taskExecutionResource.getTaskName(),
					taskExecutionResource.getEndTime(),
					taskExecutionResource.getExitCode());
		}
		assertThat(taskExecutionResources).hasSize(0);
	}

	@Test
	public void ctrSplit() {
		// splitTest COMPLETE
		// splitTest-t1 COMPLETE
		// splitTest-t2 COMPLETE
		// splitTest-t3 COMPLETE
		//
		// splitTest exit 0
		// splitTest-t1 exit 0
		// splitTest-t2 exit 0
		// splitTest-t3 exit 0
		String taskDefinitionName = composedTaskLaunch("<t1:timestamp || t2:timestamp || t3:timestamp>");
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		taskExecutionResources = getTaskExecutionResource(taskDefinitionName + "-t1");
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		taskExecutionResources = getTaskExecutionResource(taskDefinitionName + "-t2");
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		taskExecutionResources = getTaskExecutionResource(taskDefinitionName + "-t3");
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);
	}

	private void assertTaskExecutions(String taskDefinitionName,
			int expectedExitCode, int expectedCount) {
		assertTrue(waitForTaskToComplete(taskDefinitionName + "-a", expectedCount));
		assertTrue(waitForTaskToComplete(taskDefinitionName + "-b", expectedCount));
		assertTrue(waitForTaskToComplete(taskDefinitionName, expectedCount));
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			logger.info("task name: {} end time: {} exit code: {}",
					taskExecutionResource.getTaskName(),
					taskExecutionResource.getEndTime(),
					taskExecutionResource.getExitCode());
			assertEquals(expectedExitCode, (int) taskExecutionResource.getExitCode());
		}
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
		assertEquals(expectedExitCode, (int) lastExecution.getExitCode());
	}

	private void assertParentTaskExecution(String taskDefinitionName,
			int expectedExitCode, int expectedCount, int expectJobCount) {

		assertTrue(waitForTaskToComplete(taskDefinitionName, expectedCount));
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);

		for (TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			logger.info("task name: {} end time: {} exit code: {}",
					taskExecutionResource.getTaskName(),
					taskExecutionResource.getEndTime(),
					taskExecutionResource.getExitCode());
			assertEquals(expectedExitCode, (int) taskExecutionResource.getExitCode());
		}
		assertEquals(expectJobCount, getJobExecutionByTaskName(taskDefinitionName).size());
	}
}
