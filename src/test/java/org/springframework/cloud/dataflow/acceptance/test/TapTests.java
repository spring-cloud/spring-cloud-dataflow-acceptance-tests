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

import org.springframework.cloud.dataflow.acceptance.test.util.Stream;

import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the for obtaining messages from a tap and
 * a destination.
 * @author Glenn Renfro
 */
public class TapTests extends AbstractStreamTests{

	@Test
	public void testDestination() {
		Stream logStream = getStream("DESTINATION1");
		logStream.setSink("log --log.expression='\"DESTINATION1 - TIMESTAMP: \".concat(payload)'");
		logStream.setDefinition(":DESTINATION1 " + " > " +logStream.getSink());
		deployStream(logStream);

		Stream timeStream = getStream("DESTINATION2");
		timeStream.setSource("time");
		timeStream.setDefinition(timeStream.getSource() + " > :DESTINATION1");
		deployStream(timeStream);

		assertTrue("Sink not started", waitForLogEntry(logStream.getSink(), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(logStream.getSink(), "DESTINATION1 - TIMESTAMP:"));
	}

	@Test
	public void tapTests() {
		Stream stream = getStream("TAPTOCK");
		stream.setSink("log");
		stream.setSource("time");
		stream.setDefinition(stream.getSource() + " | " +stream.getSink());
		deployStream(stream);

		Stream tapStream = getStream("TAPSTREAM");
		tapStream.setSink("log --log.expression='\"TAPSTREAM - TIMESTAMP: \".concat(payload)'");
		tapStream.setDefinition(" :TAPTOCK.time > " +tapStream.getSink());
		deployStream(tapStream);

		assertTrue("Sink not started", waitForLogEntry(tapStream.getSink(), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(tapStream.getSink(), "TAPSTREAM - TIMESTAMP:"));
	}

}
