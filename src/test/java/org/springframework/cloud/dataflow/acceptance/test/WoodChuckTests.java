/*
 * Copyright 2017 the original author or authors.
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

import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;

import org.junit.Test;
import org.springframework.cloud.dataflow.acceptance.test.util.StreamDefinition;

public class WoodChuckTests extends AbstractStreamTests {

	@Test
	public void testPartitioning() throws URISyntaxException {
		StreamDefinition stream = StreamDefinition.builder("WOODCHUCK-TEST")
				.definition("http | log")
				.addProperty("deployer.*.count", "2")
				.build();
		deployStream(stream);

		assertTrue("Source not started", waitForLogEntry(stream.getApplication("http"), "Started HttpSource"));
		assertTrue("Sink not started", waitForLogEntry(stream.getApplication("log"), "Started LogSink"));

		// post data with a counter which should partition
		// data evenly as partitioning is just a simple dummy hash
		for (int i = 1; i < 11; i++) {
			httpPostData(stream.getApplication("http"), "xxxx" + i);
		}
		// expect logging to get changed adding `acctests:${instance.index}`
		// with local a single attempt should be fine as multiple apps write to same file,
		// cf have different files so with 2 partitions round robin should work with 2 attempts.
		assertTrue("No output found", waitForLogEntry(2, stream.getApplication("log"), "acctests:1", "xxxx1", "xxxx3", "xxxx5", "xxxx7", "xxxx9"));
		assertTrue("No output found", waitForLogEntry(2, stream.getApplication("log"), "acctests:0", "xxxx2", "xxxx4", "xxxx6", "xxxx8", "xxxx10"));
	}
}
