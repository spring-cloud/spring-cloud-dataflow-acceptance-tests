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

import java.util.UUID;

import org.junit.Test;

import org.springframework.cloud.dataflow.acceptance.test.util.Stream;

import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the http source app as a part of a stream.
 * @author Glenn Renfro
 * @author Thomas Risberg
 */

public class HttpSourceTests extends AbstractStreamTests {

	@Test
	public void httpSourceTests() throws Exception{
		Stream stream = getStream("HTTP-TEST");
		stream.setSink("log");
		stream.setSource("http");
		stream.setDefinition(stream.getSource() + " | " +stream.getSink());
		deployStream(stream);
		String testVal = UUID.randomUUID().toString();
		assertTrue("Source not started", waitForLogEntry(stream.getSource(), "Started HttpSource"));
		httpPostData(stream.getSource(), testVal);
		assertTrue("Sink not started", waitForLogEntry(stream.getSink(), "Started LogSink"));
		assertTrue("No output found", waitForLogEntry(stream.getSink(), testVal));
	}

}
