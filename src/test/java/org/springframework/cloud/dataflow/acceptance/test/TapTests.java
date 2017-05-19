/*
 * Copyright 2016 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.dataflow.acceptance.test;

import org.junit.Test;

import org.springframework.cloud.dataflow.acceptance.test.util.StreamDefinition;

import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the for obtaining messages from a tap and
 * a destination.
 * @author Glenn Renfro
 * @author Vinicius Carvalho
 */
public class TapTests extends AbstractStreamTests{

	@Test
	public void testDestination() {
		StreamDefinition logStream = StreamDefinition.builder("DESTINATION1")
				.definition(":DESTINATION1 > log")
				.build();
		deployStream(logStream);

		StreamDefinition timeStream = StreamDefinition.builder("DESTINATION2")
				.definition("time > :DESTINATION1")
				.build();
		deployStream(timeStream);


		assertTrue("Sink not started", waitForLogEntry(logStream.getApplication("log"), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(logStream.getApplication("log"), ".DESTINATION1-"));

	}

	@Test
	public void tapTests() {
		StreamDefinition stream = StreamDefinition.builder("TAPTOCK")
				.definition("time | log")
				.build();
		deployStream(stream);

		StreamDefinition tapStream = StreamDefinition.builder("TAPSTREAM")
				.definition(":TAPTOCK.time > log")
				.build();
		deployStream(tapStream);
		assertTrue("Sink not started", waitForLogEntry(tapStream.getApplication("log"), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(tapStream.getApplication("log"), "time.TAPSTREAM-"));

	}

}
