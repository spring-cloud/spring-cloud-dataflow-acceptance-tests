package org.springframework.cloud.dataflow.acceptance.test;

import org.junit.Test;

/**
 * @author Glenn Renfro
 */
public class TransformTests extends AbstractStreamTests{
	@Test
	public void logTests() {
		final String PROCESSOR_NAME = "transform1";
		Stream stream = getStream("transform-test");
		stream.setSink("log");
		stream.setSource("http --port=9000");
		stream.addProcessor(PROCESSOR_NAME,
				"transform --expression=payload.toUpperCase()");

		stream.setDefinition(stream.getSource() + " | " +
				stream.getProcessors().get(PROCESSOR_NAME) + " | "
				+ stream.getSink());

		deployStream(stream);

		httpPostData(stream.getSource(), 9000, "abcdefg");
		waitForLogEntry(5000, stream.getSink(), "ABCDEFG");
	}
}
