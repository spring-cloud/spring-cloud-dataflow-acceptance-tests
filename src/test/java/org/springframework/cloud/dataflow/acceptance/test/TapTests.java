package org.springframework.cloud.dataflow.acceptance.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

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
