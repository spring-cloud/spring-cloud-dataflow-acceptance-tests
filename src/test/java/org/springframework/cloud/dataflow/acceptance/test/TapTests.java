package org.springframework.cloud.dataflow.acceptance.test;

import org.junit.Test;

/**
 * @author Glenn Renfro
 */
public class TapTests extends AbstractStreamTests{

	@Test
	public void testDestination() {
		Stream logStream = getStream("destination1");
		logStream.setSink("log");
		logStream.setDefinition(":destination1 " + " > " +logStream.getSink());
		deployStream(logStream);

		Stream timeStream = getStream("destination2");
		timeStream.setSource("time");
		timeStream.setDefinition(timeStream.getSource() + " > :destination1");
		deployStream(timeStream);

		waitForLogEntry(5000, logStream.getSink(), "Started LogSinkRabbitApplication");
		waitForLogEntry(5000, logStream.getSink(), "] log.sink");
	}

	@Test
	public void tapTests() {
		Stream stream = getStream("taptock");
		stream.setSink("log");
		stream.setSource("time");
		stream.setDefinition(stream.getSource() + " | " +stream.getSink());
		deployStream(stream);

		Stream tapStream = getStream("tapStream");
		tapStream.setSink("log");
		tapStream.setDefinition(" :taptock.time > " +tapStream.getSink());
		deployStream(tapStream);

		waitForLogEntry(5000, tapStream.getSink(), "Started LogSinkRabbitApplication");
		waitForLogEntry(5000, tapStream.getSink(), "] log.sink");
	}
}
