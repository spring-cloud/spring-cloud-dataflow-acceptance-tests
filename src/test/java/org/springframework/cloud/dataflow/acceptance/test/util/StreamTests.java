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

package org.springframework.cloud.dataflow.acceptance.test.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the basic functionality of the Stream util.
 *
 * @author Glenn Renfro
 * @author Thomas Risberg
 */
public class StreamTests {

	@Test
	public void testStreamSourceSink() {
		Stream stream = new Stream("test1");
		stream.setSink("BAR");
		stream.setSource("FOO");
		stream.setDefinition(stream.getSource() + " | " + stream.getSink());
		assertEquals ("FOO | BAR", stream.getDefinition());
	}

	@Test
	public void testStreamSourceProcessorSink() {
		Stream stream = new Stream("test2");
		stream.setSink("BAR");
		stream.setSource("FOO");
		stream.addProcessor("BAZ","BAZBAZ");
		stream.setDefinition(stream.getSource() + " | " +
				stream.getProcessors().get("BAZ") + " | " +
				stream.getSink());
		assertEquals ("FOO | BAZBAZ | BAR", stream.getDefinition());
	}

	@Test
	public void testTAP() {
		Stream stream = new Stream("test3");
		stream.setSink("BAR");
		stream.setDefinition(":WOMBAT.time > " + stream.getSink());
		assertEquals (":WOMBAT.time > BAR", stream.getDefinition());
	}

}
