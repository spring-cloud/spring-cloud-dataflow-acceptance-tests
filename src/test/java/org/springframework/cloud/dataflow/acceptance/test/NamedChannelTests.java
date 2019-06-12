/*
 * Copyright 2016 the original author or authors.
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

import org.junit.Ignore;
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
	public void testDestination() throws Exception {
		StreamDefinition logStream = deployLog("LOG-DESTINATION-SINK", ":LOG-DESTINATION > log");
		StreamDefinition httpStream = deployHttp("HTTP-DESTINATION-SOURCE", "http > :LOG-DESTINATION");

		httpPostData(httpStream.getApplication("http"), "abcdefg");

		assertTrue("No output found", waitForLogEntry(logStream.getApplication("log"), "abcdefg"));
	}

	@Test
	public void testTap() throws Exception {
		StreamDefinition stream = deployHttp("TAPHTTP", "http | log");
		// make sure log is running as well
		assertTrue(String.format("'%s' not started", stream.getDefinition()),
				waitForLogEntry(stream.getApplication("log"), "Started LogSink"));
		StreamDefinition tapStream = deployLog("TAPSTREAM", ":TAPHTTP.http > log");

		httpPostData(stream.getApplication("http"), "abcdefg");

		assertTrue("No output found", waitForLogEntry(tapStream.getApplication("log"), "abcdefg"));
	}

	@Test
	public void manyToOneTest() throws Exception {
		StreamDefinition logStream = deployLog("MANY-TO-ONE", ":MANY-TO-ONE-DESTINATION > log");
		StreamDefinition httpStreamOne = deployHttp("HTTP-SOURCE-1", "http > :MANY-TO-ONE-DESTINATION");
		StreamDefinition httpStreamTwo = deployHttp("HTTP-SOURCE-2", "http > :MANY-TO-ONE-DESTINATION");

		httpPostData(httpStreamOne.getApplication("http"), "abcdefg");
		assertTrue("No output found", waitForLogEntry(logStream.getApplication("log"), "abcdefg"));

		httpPostData(httpStreamTwo.getApplication("http"), "hijklmn");
		assertTrue("No output found", waitForLogEntry(logStream.getApplication("log"), "hijklmn"));
	}

	@Test
	@Ignore
	public void directedGraphTest() throws Exception {
		StreamDefinition fooLogStream = deployLog("DIRECTED-GRAPH-DESTINATION1",
				":foo > transform --expression=payload+'-foo' | log");
		StreamDefinition barLogStream = deployLog("DIRECTED-GRAPH-DESTINATION2",
				":bar > transform --expression=payload+'-bar' | log");
		StreamDefinition httpStream = deployHttp("DIRECTED-GRAPH-HTTP-SOURCE",
				"http | router --expression=payload.contains('a')?'foo':'bar'");

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
		StreamDefinition logDefinition = StreamDefinition.builder(streamName)
				.definition(definition).build();
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
