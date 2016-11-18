package org.springframework.cloud.dataflow.acceptance.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the timestamp task.
 * @author Glenn Renfro
 */
public class TimestampTaskTests extends AbstractTaskTests{

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
		assertTrue(waitForTaskToComplete("task-foobar", 0));
	}

	private void assertTaskExecutions(String taskDefinitionName,
			int expectedExitCode, int expectedCount) {
		List<TaskExecutionResource> taskExecutionResources =
				getTaskExecutionResource(taskDefinitionName);
		assertTrue(waitForTaskToComplete(taskDefinitionName, expectedCount));

		for(TaskExecutionResource taskExecutionResource : taskExecutionResources) {
			assertEquals(expectedExitCode, taskExecutionResource.getExitCode());
		}
	}

	@Override
	public List<AbstractTaskTests.TaskTestTypes> getTarget() {
		List<AbstractTaskTests.TaskTestTypes> types = new ArrayList<>();
		types.add(AbstractTaskTests.TaskTestTypes.TIMESTAMP);
		types.add(AbstractTaskTests.TaskTestTypes.CORE);
		return types;
	}

}
