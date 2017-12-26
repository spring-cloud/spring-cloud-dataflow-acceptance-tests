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

import java.net.URISyntaxException;

import org.junit.Test;

import org.springframework.cloud.dataflow.acceptance.test.util.StreamDefinition;

import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the woodchuck demo.
 * @author Janne Valkealahti
 * @author Thomas Risberg
 */
public class WoodChuckTests extends AbstractStreamTests {

	@Test
	public void testPartitioning() throws URISyntaxException {
		StreamDefinition stream = StreamDefinition.builder("WOODCHUCK-TEST")
				.definition("http | splitter --expression=payload.split(' ') | log")
				.addProperty("deployer.log.count", "2")
				.addProperty("app.splitter.producer.partitionKeyExpression", "payload")
				.addProperty("app.log.spring.cloud.stream.kafka.bindings.input.consumer.autoRebalanceEnabled", "false")
				.addProperty("app.log.logging.pattern.level", "WOODCHUCK-${INSTANCE_INDEX} %5p")
				.build();
		deployStream(stream);

		assertTrue("Source not started", waitForLogEntry(stream.getApplication("http"), "Started HttpSource"));
		assertTrue("Processor not started", waitForLogEntry(stream.getApplication("splitter"), "Started SplitterProcessor"));
		assertTrue("Sink not started", waitForLogEntry(stream.getApplication("log"), "Started LogSink"));

		httpPostData(stream.getApplication("http"), "How much wood would a woodchuck chuck if a woodchuck could chuck wood");

		// expect logging to have `WOODCHUCK-${instance.index}`
		assertTrue("No output found", waitForLogEntry(stream.getApplication("log"),  "WOODCHUCK-0", "How", "chuck"));
		assertTrue("No output found", waitForLogEntry(stream.getApplication("log"), "WOODCHUCK-1", "much", "wood", "would", "if", "a", "woodchuck", "could"));
	}
}
