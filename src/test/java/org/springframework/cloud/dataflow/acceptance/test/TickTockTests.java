/*
 * Copyright 2017-2020 the original author or authors.
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

import java.time.Duration;
import java.util.Collection;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.dataflow.acceptance.test.util.StreamDefinition;
import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.about.AboutResource;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.StatusCode;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.util.AssertionErrors.assertTrue;


/**
 * Executes acceptance tests for the ticktock demo.
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author Vinicius Carvalho
 * @author Ilayaperumal Gopinathan
 * @author Christian Tzolov
 */

public class TickTockTests extends AbstractStreamTests {

	@Test
	public void tickTockTests() {
		StreamDefinition stream = StreamDefinition.builder("TICKTOCK")
				.definition("time | log")
				.addProperty("app.log.log.expression", "'TICKTOCK - TIMESTAMP: '.concat(payload)")
				.build();

		deployStream(stream);
		assertTrue("Source not started", waitForLogEntry(stream.getApplication("time"), "Started TimeSource"));
		assertTrue("Sink not started", waitForLogEntry(stream.getApplication("log"), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(stream.getApplication("log"), "TICKTOCK - TIMESTAMP:"));
	}

	@Test
	public void tickTockUpdateRollbackTests() {
		AboutResource aboutResource = this.dataFlowOperations.aboutOperation().get();
		String implementation = aboutResource.getVersionInfo().getImplementation().getName();
		if (!implementation.equals("spring-cloud-dataflow-server")) {
			assumeTrue("true".equalsIgnoreCase(String.valueOf(aboutResource.getFeatureInfo().isStreamsEnabled())), () -> "Skipping test");
		}
		this.dataFlowOperations.streamOperations().destroyAll();
		StreamDefinition stream = StreamDefinition.builder("TICKTOCK")
				.definition("time | log")
				.addProperty("app.log.log.expression", "'TICKTOCK - TIMESTAMP: '.concat(payload)")
				.build();

		deployStream(stream);
		assertTrue("Source not started", waitForLogEntry(stream.getApplication("time"), "Started TimeSource"));
		assertTrue("Sink not started", waitForLogEntry(stream.getApplication("log"), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(stream.getApplication("log"), "TICKTOCK - TIMESTAMP:"));
		StreamDefinitionResource updatedStream = updateStream(stream,
				"app.log.log.expression='TICKTOCK Updated - TIMESTAMP: '.concat(payload)", null);
		assertTrue("Ticktock Message not found", updatedStream.getDslText()
				.contains("--log.expression=\"'TICKTOCK Updated - TIMESTAMP: '.concat(payload)\""));
		waitForUpdateOrRollback(stream);

		assertTrue("Sink not started", waitForLogEntry(stream.getApplication("log"), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(stream.getApplication("log"), "TICKTOCK Updated - TIMESTAMP:"));

		rollbackStream(stream);
		waitForUpdateOrRollback(stream);
		assertTrue("No output found", waitForLogEntry(stream.getApplication("log"), "TICKTOCK - TIMESTAMP:"));

	}

	@Test
	public void tickTockTestsFromConfigServer() {
		String platformType = System.getProperty("PLATFORM_TYPE", "");
		String skipCloudConfig = System.getProperty("SKIP_CLOUD_CONFIG", "false");
		assumeTrue("cloudfoundry".equalsIgnoreCase(platformType), () -> "Skipping test" );
		assumeTrue("false".equalsIgnoreCase(skipCloudConfig), () -> "Skipping test");
		StreamDefinition stream = StreamDefinition.builder("TICKTOCK-config-server")
				.definition("time | log")
				.addProperty("app.log.spring.profiles.active", "test")
				.addProperty("deployer.log.cloudfoundry.services", "cloud-config-server")
				.addProperty("app.log.spring.cloud.config.name", "MY_CONFIG_TICKTOCK_LOG_NAME")
				.build();
		deployStream(stream);
		assertTrue("Source not started", waitForLogEntry(stream.getApplication("time"), "Started TimeSource"));
		assertTrue("Sink not started", waitForLogEntry(stream.getApplication("log"), "Started LogSink"));
		assertTrue("No output found",
				waitForLogEntry(stream.getApplication("log"), "TICKTOCK CLOUD CONFIG - TIMESTAMP:"));
	}

	private void waitForUpdateOrRollback(final StreamDefinition stream) {
		// In the AT environment there will be many releases over time until the database is
		// reinitialized.
		await().atMost(Duration.ofMinutes(2)).until(() -> {
			Collection<Release> history = dataFlowOperations.streamOperations().history(stream.getName());
			long deployCount = history.stream()
					.filter(release -> release.getInfo().getStatus().getStatusCode().equals(StatusCode.DEPLOYED))
					.count();

			long deletedCount = history.stream()
					.filter(release -> release.getInfo().getStatus().getStatusCode().equals(StatusCode.DELETED))
					.count();

			logger.debug(
					"Total = " + history.size() + " Deploy Count = " + deployCount + " Deleted Count = "
							+ deletedCount);
			return (deployCount == 1) && deletedCount == history.size() - 1;
		});
	}
}
