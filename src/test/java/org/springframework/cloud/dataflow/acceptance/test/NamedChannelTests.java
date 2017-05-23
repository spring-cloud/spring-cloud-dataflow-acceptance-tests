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
 * Executes acceptance tests for Named Channels.
 * @author Glenn Renfro
 * @author Vinicius Carvalho
 */
public class NamedChannelTests extends AbstractStreamTests {

	@Test
	public void testDestination() throws Exception{
		StreamDefinition logStream = deployLog("LOG_DESTINATION_SINK", ":LOG_DESTINATION > log");
		StreamDefinition httpStream = deployHttp("HTTP_DESTINATION_SOURCE", "http > :LOG_DESTINATION");

		httpPostData(httpStream.getApplication("http"), "abcdefg");

		assertTrue("No output found", waitForLogEntry(logStream.getApplication("log"), "abcdefg"));
	}

	@Test
	public void testTap() throws Exception{
		StreamDefinition stream = deployHttp("TAPHTTP", "http | log");
		//make sure log is running as well
		assertTrue(String.format("'%s' not started", stream.getDefinition()),
				waitForLogEntry(stream.getApplication("log"), "Started LogSink"));
		StreamDefinition tapStream = deployLog("TAPSTREAM", ":TAPHTTP.http > log");

		httpPostData(stream.getApplication("http"), "abcdefg");

		assertTrue("No output found", waitForLogEntry(tapStream.getApplication("log"), "abcdefg"));
	}

	@Test
	public void manyToOneTest() throws Exception {
		StreamDefinition logStream = deployLog("MANY_TO_ONE", ":MANY_TO_ONE_DESTINATION > log");
		StreamDefinition httpStreamOne = deployHttp("HTTP_SOURCE_1", "http > :MANY_TO_ONE_DESTINATION");
		StreamDefinition httpStreamTwo = deployHttp("HTTP_SOURCE_2", "http > :MANY_TO_ONE_DESTINATION");

		httpPostData(httpStreamOne.getApplication("http"), "abcdefg");
		assertTrue("No output found", waitForLogEntry(logStream.getApplication("log"), "abcdefg"));

		httpPostData(httpStreamTwo.getApplication("http"), "hijklmn");
		assertTrue("No output found", waitForLogEntry(logStream.getApplication("log"), "hijklmn"));
	}

	@Test
	public void directedGraphTest() throws Exception {
		StreamDefinition fooLogStream = deployLog("DIRECTED_GRAPH_DESTINATION1", ":foo > transform --expression=payload+'-foo' | log");
		StreamDefinition barLogStream = deployLog("DIRECTED_GRAPH_DESTINATION2", ":bar > transform --expression=payload+'-bar' | log");
		StreamDefinition httpStream = deployHttp("DIRECTED_GRAPH_HTTP_SOURCE","http | router --expression=payload.contains('a')?'foo':'bar'");

		httpPostData(httpStream.getApplication("http"), "abcd");
		httpPostData(httpStream.getApplication("http"), "defg");

		assertTrue("No output found", waitForLogEntry(fooLogStream.getApplication("log"), "abcd-foo"));
		assertTrue("No output found", waitForLogEntry(barLogStream.getApplication("log"), "defg-bar"));

	}

	private void deployStream(StreamDefinition definition, String appToMonitor, String startMessage) {
		deployStream(definition);
		assertTrue(String.format("'%s' not started", definition.getDefinition()),
				waitForLogEntry(definition.getApplication(appToMonitor), startMessage));
	}

	private StreamDefinition deployLog(String streamName, String definition) {
		StreamDefinition.StreamDefinitionBuilder logBuilder = StreamDefinition.builder(streamName)
				.definition(definition);
		StreamDefinition logDefinition = logBuilder.build();
		deployStream(logDefinition, "log", "Started LogSink");
		return logDefinition;
	}

	private StreamDefinition deployHttp(String streamName, String definition) {
		StreamDefinition httpDefinition = StreamDefinition.builder(streamName)
				.definition(definition)
				.build();
		deployStream(httpDefinition, "http", "Started HttpSource");
		return httpDefinition;
	}
}
