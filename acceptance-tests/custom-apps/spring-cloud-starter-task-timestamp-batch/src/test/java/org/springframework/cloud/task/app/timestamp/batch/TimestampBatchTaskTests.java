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

package org.springframework.cloud.task.app.timestamp.batch;

import org.junit.Rule;
import org.junit.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.rule.OutputCapture;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

/**
 * Verifies that the Task Application outputs the correct Batch and Task log entries.
 *
 * @author Glenn Renfro
 */
public class TimestampBatchTaskTests {

	@Rule
	public OutputCapture outputCapture = new OutputCapture();

	@Test
	public void testTimeStampApp() throws Exception {
		final String TEST_DATE_DOTS = ".......";
		final String CREATE_TASK_MESSAGE = "Creating: TaskExecution{executionId=";
		final String UPDATE_TASK_MESSAGE = "Updating: TaskExecution with executionId=1 with the following";
		final String JOB1_MESSAGE = "Job1 was run with date ";
		final String JOB2_MESSAGE = "Job2 was run with date ";

		String[] args = {"--timestamp.format=yyyy" + TEST_DATE_DOTS};

		SpringApplication
				.run(TestTimestampBatchTaskApplication.class, args);

		String output = this.outputCapture.toString();

		assertThat(output, containsString(TEST_DATE_DOTS));
		assertThat(output, containsString(CREATE_TASK_MESSAGE));
		assertThat(output, containsString(UPDATE_TASK_MESSAGE));

		assertThat(output, containsString(JOB1_MESSAGE));
		assertThat(output, containsString(JOB2_MESSAGE));

	}


	@SpringBootApplication
	public static class TestTimestampBatchTaskApplication {
		public static void main(String[] args) {
			SpringApplication.run(TestTimestampBatchTaskApplication.class, args);
		}
	}

}
