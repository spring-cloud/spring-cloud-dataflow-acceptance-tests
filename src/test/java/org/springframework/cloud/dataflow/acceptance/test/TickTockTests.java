/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.acceptance.test;

import org.hamcrest.Matchers;
import org.junit.Test;

import org.springframework.cloud.dataflow.acceptance.test.util.StreamDefinition;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

/**
 * Executes acceptance tests for the ticktock demo.
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author Vinicius Carvalho
 * @author Ilayaperumal Gopinathan
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
	public void tickTockTestsFromConfigServer() {
		String platformType = System.getProperty("PLATFORM_TYPE", "");
		assumeThat("Skipping test", "cloudfoundry", Matchers.equalToIgnoringCase(platformType));
		StreamDefinition stream = StreamDefinition.builder("TICKTOCK-config-server")
				.definition("time | log")
				.addProperty("app.log.spring.profiles.active", "test")
				.build();
		deployStream(stream);
		assertTrue("Source not started", waitForLogEntry(stream.getApplication("time"), "Started TimeSource"));
		assertTrue("Sink not started", waitForLogEntry(stream.getApplication("log"), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(stream.getApplication("log"), "TICKTOCK CLOUD CONFIG - TIMESTAMP:"));
	}
}
