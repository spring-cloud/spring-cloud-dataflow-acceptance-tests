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

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import org.springframework.cloud.dataflow.acceptance.test.util.Stream;

/**
 * Executes acceptance tests for the for obtaining messages from a tap and
 * a destination.
 * @author Glenn Renfro
 */
public class TapTests extends AbstractStreamTests{

	@Test
	public void testDestination() {
		Stream logStream = getStream("DESTINATION1");
		logStream.setSink("log");
		logStream.setDefinition(":DESTINATION1 " + " > " +logStream.getSink());
		deployStream(logStream);

		Stream timeStream = getStream("DESTINATION2");
		timeStream.setSource("time");
		timeStream.setDefinition(timeStream.getSource() + " > :DESTINATION1");
		deployStream(timeStream);

		waitForLogEntry(logStream.getSink(), "Started LogSinkRabbitApplication");
		waitForLogEntry(logStream.getSink(), "] log.sink");
	}

	@Test
	public void tapTests() {
		Stream stream = getStream("TAPTOCK");
		stream.setSink("log");
		stream.setSource("time");
		stream.setDefinition(stream.getSource() + " | " +stream.getSink());
		deployStream(stream);

		Stream tapStream = getStream("TAPSTREAM");
		tapStream.setSink("log");
		tapStream.setDefinition(" :TAPTOCK.time > " +tapStream.getSink());
		deployStream(tapStream);

		waitForLogEntry(tapStream.getSink(), "Started LogSinkRabbitApplication");
		waitForLogEntry(tapStream.getSink(), "] log.sink");
	}

	@Override
	public List<StreamTestTypes> getTarget() {
		List<StreamTestTypes> types = new ArrayList<>();
		types.add(StreamTestTypes.TAP);
		return types;
	}
}
