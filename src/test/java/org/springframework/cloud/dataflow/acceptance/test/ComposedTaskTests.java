/*
 * Copyright 2019-2021 the original author or authors.
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.dataflow.rest.client.DataFlowClientException;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executes acceptance tests for the composed tasks.
 *
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 * @author David Turanski
 */
public class ComposedTaskTests extends AbstractTaskTests {

	@Test
	public void ctrLaunchTest() {
		String taskDefinitionName = composedTaskLaunch("a: timestamp && b:timestamp");
		assertTaskExecutions(taskDefinitionName, 0, 1);

		Collection<TaskExecutionResource> taskExecutions = this.taskOperations.executionListByTaskName(taskDefinitionName).getContent();
		List<Long> jobExecutionIds = taskExecutions.toArray(new TaskExecutionResource[0])[0].getJobExecutionIds();
		assertThat(jobExecutionIds.size()).isEqualTo(1);
		Exception exception = assertThrows(DataFlowClientException.class, () -> {
			restartJob(jobExecutionIds.get(0));
		});
		assertTrue(exception.getMessage().contains(" and state 'COMPLETED' is not restartable"));
	}

	@Disabled("This behavior is currently dependent on SCDF version")
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
		String taskDefinitionName = composedTaskLaunch("scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false && timestamp");
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

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
	}

	@Test
	public void ctrSequential() {
		String taskDefinitionName = composedTaskLaunch("t1:timestamp && t2:timestamp && t3:timestamp");
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
	}

	@Test
	public void ctrSequentialTransitionAndSplitWithScenarioFailed() {
		String taskDefinitionName = composedTaskLaunch("t1: timestamp && scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED'->t3: timestamp && <t4: timestamp || t5: timestamp> && t6: timestamp");
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-scenario", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "scenario", 1);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
		verifyWorkFlowElementNoExecution(taskDefinitionName, "t4");
	}

	@Test
	public void ctrSequentialTransitionAndSplitWithScenarioOk() {
		String taskDefinitionName = composedTaskLaunch("t1: timestamp && t2: scenario 'FAILED'->t3: timestamp && <t4: timestamp || t5: timestamp> && t6: timestamp");
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t4", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t5", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t6", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t4", 0);
		verifyWorkFlowElement(taskDefinitionName, "t5", 0);
		verifyWorkFlowElement(taskDefinitionName, "t6", 0);
	}

	@Test
	public void ctrNestedSplit() {
		String taskDefinitionName = composedTaskLaunch("<<t1: timestamp || t2: timestamp > && t3: timestamp || t4: timestamp>");
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t4", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);
		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t4", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
	}

	@Test
	public void testEmbeddedFailedGraph() {
		String taskDefinitionName = composedTaskLaunch(String.format(
				"a: timestamp && b:scenario  --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true && c:timestamp", randomJobName()),
				Collections.EMPTY_MAP, Collections.emptyList());
		assertTaskExecutions(taskDefinitionName, 0, 1);
		Collection<TaskExecutionResource> taskExecutions = this.taskOperations
				.executionListByTaskName(taskDefinitionName).getContent();
		List<Long> jobExecutionIds = taskExecutions.toArray(new TaskExecutionResource[0])[0].getJobExecutionIds();
		assertThat(jobExecutionIds.size()).isEqualTo(1);
		restartJob(jobExecutionIds.get(0));
		assertParentTaskExecution(taskDefinitionName, 0, 2, 2);
	}

	@Test
	public void twoSplitTest() {
		String taskDefinitionName = composedTaskLaunch("<t1: timestamp ||t2: timestamp||t3: timestamp> && <t4: timestamp||t5: timestamp>", Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t4", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t5", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
		verifyWorkFlowElement(taskDefinitionName, "t4", 0);
		verifyWorkFlowElement(taskDefinitionName, "t5", 0);
	}

	@Test
	public void sequentialAndSplitTest() {
		String taskDefinitionName = composedTaskLaunch("<t1: timestamp && <t2: timestamp || t3: timestamp || t4: timestamp> && t5: timestamp>", Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t4", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t5", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
		verifyWorkFlowElement(taskDefinitionName, "t4", 0);
		verifyWorkFlowElement(taskDefinitionName, "t5", 0);
	}

	@Test
	public void sequentialTransitionAndSplitFailedInvalidTest() {
		String taskDefinitionName = composedTaskLaunch("t1: timestamp && b:scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t2: timestamp && t3: timestamp && t4: timestamp && <t5:timestamp || t6: timestamp> && t7: timestamp", Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "b", 1);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElementNoExecution(taskDefinitionName, "t3");
	}

	@Test
	public void sequentialAndSplitWithFlowTest() {
		String taskDefinitionName = composedTaskLaunch("t1: timestamp && <t2: timestamp && t3: timestamp || t4: timestamp ||t5: timestamp> && t6: timestamp", Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t4", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t5", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t6", 1)).isTrue();

		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
		verifyWorkFlowElement(taskDefinitionName, "t4", 0);
		verifyWorkFlowElement(taskDefinitionName, "t5", 0);
		verifyWorkFlowElement(taskDefinitionName, "t6", 0);

	}

	@Test
	public void sequentialAndFailedSplitTest() {
		String taskDefinitionName = composedTaskLaunch(String.format(
				"t1: timestamp && <t2: timestamp ||b:scenario --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true || t3: timestamp> && t4: timestamp", randomJobName()),
				Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();

		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "b", 1);
		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
		verifyWorkFlowElementNoExecution(taskDefinitionName, "t4");

		Collection<TaskExecutionResource> taskExecutions = this.taskOperations.executionListByTaskName(taskDefinitionName).getContent();
		List<Long> jobExecutionIds = taskExecutions.toArray(new TaskExecutionResource[0])[0].getJobExecutionIds();
		assertThat(jobExecutionIds.size()).isEqualTo(1);
		restartJob(jobExecutionIds.get(0));
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b", 2)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t4",1)).isTrue();
		assertParentTaskExecution(taskDefinitionName, 0, 2, 2);

	}

	@Test
	public void failedBasicTransitionTest() {
		String taskDefinitionName = composedTaskLaunch("b: scenario --io.spring.fail-task=true --io.spring.launch-batch-job=false 'FAILED' -> t1: timestamp * ->t2: timestamp", Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();

		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t1", 0);
		verifyWorkFlowElement(taskDefinitionName, "b", 1);
		verifyWorkFlowElementNoExecution(taskDefinitionName, "t2");
	}

	@Test
	public void successBasicTransitionTest() {
		String taskDefinitionName = composedTaskLaunch("b: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: timestamp * ->t2: timestamp", Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t2", 1)).isTrue();

		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();

		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(0);

		verifyWorkFlowElement(taskDefinitionName, "t2", 0);
		verifyWorkFlowElement(taskDefinitionName, "b", 0);
		verifyWorkFlowElementNoExecution(taskDefinitionName, "t1");
	}

	@Test
	public void basicTransitionWithTransitionTest() {
		String taskDefinitionName = composedTaskLaunch("b1: scenario  --io.spring.launch-batch-job=false 'FAILED' -> t1: timestamp  && b2: scenario --io.spring.launch-batch-job=false 'FAILED' -> t2: timestamp * ->t3: timestamp ", Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		verifyWorkFlowElement(taskDefinitionName, "b1", 0);
		verifyWorkFlowElement(taskDefinitionName, "b2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
		verifyWorkFlowElementNoExecution(taskDefinitionName, "t1");
		verifyWorkFlowElementNoExecution(taskDefinitionName, "t2");
	}

	@Test
	public void wildCardOnlyInLastPositionTest() {
		String taskDefinitionName = composedTaskLaunch("b1: scenario --io.spring.launch-batch-job=false 'FAILED' -> t1: timestamp  && b2: scenario --io.spring.launch-batch-job=false * ->t3: timestamp ", Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b2", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t3", 1)).isTrue();
		verifyWorkFlowElement(taskDefinitionName, "b1", 0);
		verifyWorkFlowElement(taskDefinitionName, "b2", 0);
		verifyWorkFlowElement(taskDefinitionName, "t3", 0);
		verifyWorkFlowElementNoExecution(taskDefinitionName, "t1");
	}

	@Test
	public void failedCTRRetryTest() {
		String taskDefinitionName = composedTaskLaunch(String.format(
				"b1:scenario --io.spring.fail-batch=true --io.spring.jobName=%s --spring.cloud.task.batch.fail-on-job-failure=true && t1:timestamp", randomJobName()),
				Collections.EMPTY_MAP, Collections.emptyList());
		assertThat(waitForTaskToComplete(taskDefinitionName + "-b1", 1)).isTrue();
		assertThat(waitForTaskToComplete(taskDefinitionName, 1)).isTrue();
		verifyWorkFlowElement(taskDefinitionName, "b1", 1);
		Collection<TaskExecutionResource> taskExecutions = this.taskOperations.executionListByTaskName(taskDefinitionName).getContent();
		List<Long> jobExecutionIds = taskExecutions.toArray(new TaskExecutionResource[0])[0].getJobExecutionIds();
		assertThat(jobExecutionIds.size()).isEqualTo(1);
		restartJob(jobExecutionIds.get(0));
		assertThat(waitForTaskToComplete(taskDefinitionName + "-t1", 1)).isTrue();
		assertParentTaskExecution(taskDefinitionName, 0, 2, 2);
	}

	private void verifyWorkFlowElement(String taskDefinitionName, String name, int exitCode) {
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName + "-" + name);
		assertThat(taskExecutionResources).hasSize(1);
		assertThat(taskExecutionResources.get(0).getExitCode()).isEqualTo(exitCode);
	}

	private void verifyWorkFlowElementNoExecution(String taskDefinitionName, String name) {
		List<TaskExecutionResource> taskExecutionResources = getTaskExecutionResource(taskDefinitionName + "-" + name);
		assertThat(taskExecutionResources).hasSize(0);
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

    private String randomJobName() {
        return "job-" + UUID.randomUUID().toString().substring(0, 10);
    }
}
