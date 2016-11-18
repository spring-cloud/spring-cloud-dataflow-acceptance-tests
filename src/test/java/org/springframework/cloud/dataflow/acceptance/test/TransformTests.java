package org.springframework.cloud.dataflow.acceptance.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

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

	@Override
	public List<StreamTestTypes> getTarget() {
		List<StreamTestTypes> types = new ArrayList<>();
		types.add(StreamTestTypes.TRANSFORM);
		return types;
	}
}
