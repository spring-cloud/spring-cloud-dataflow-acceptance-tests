/*
 * Copyright 2016-2017 the original author or authors.
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
 * Executes acceptance tests for the ticktock demo.
 * @author Glenn Renfro
 * @author Thomas Risberg
 */

public class TickTockTests extends AbstractStreamTests {

	@Test
	public void tickTockTests() {
		Stream stream = getStream("TICKTOCK");
		stream.setSink("log --log.expression='\"TICKTOCK - TIMESTAMP: \".concat(payload)'");
		stream.setSource("time");
		stream.setDefinition(stream.getSource() + " | " +stream.getSink());
		deployStream(stream);
		assertTrue("Source not started", waitForLogEntry(stream.getSource(), "Started TimeSource"));
		assertTrue("Sink not started", waitForLogEntry(stream.getSink(), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(stream.getSink(), "TICKTOCK - TIMESTAMP:"));
	}

}
