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

/**
 * Executes acceptance tests for the transform processor app as a part of a stream.
 * @author Glenn Renfro
 */
public class TransformTests extends AbstractStreamTests{
	@Test
	public void transformTests() throws Exception{
		final String PROCESSOR_NAME = "transform1";
		Stream stream = getStream("TRANSFORM-TEST");
		stream.setSink("log");
		stream.setSource("http");
		stream.addProcessor(PROCESSOR_NAME,
				"transform --expression=payload.toUpperCase()");

		stream.setDefinition(stream.getSource() + " | " +
				stream.getProcessors().get(PROCESSOR_NAME) + " | "
				+ stream.getSink());

		deployStream(stream);

		httpPostData(stream.getSource(), "abcdefg");
		waitForLogEntry(stream.getSink(), "ABCDEFG");
	}

}
