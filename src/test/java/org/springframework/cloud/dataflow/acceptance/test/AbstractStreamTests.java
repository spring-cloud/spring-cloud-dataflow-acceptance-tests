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
 *
 */

package org.springframework.cloud.dataflow.acceptance.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.dataflow.rest.client.AppRegistryOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.StreamOperations;
import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

/**
 * Abstract base class that is used by stream acceptance tests.  This class
 * contains commonly used utility methods for acceptance tests as well as the
 * ability to dump logs of apps when a stream acceptance test fails.
 * @author Glenn Renfro
 */
@RunWith(SpringRunner.class)
public abstract class AbstractStreamTests implements InitializingBean {

	private static final String LOCAL_PLATFORM = "local";

	private static final String RABBIT_BINDER = "RABBIT";

	private static final String KAFKA_BINDER = "KAFKA";

	@Value("${deploy_pause_time:5}")
	protected int deployPauseTime;

	@Value("${deploy_pause_retries:25}")
	protected int deployPauseRetries;

	@Value("${SERVER_HOST:http://localhost}")
	protected String serverHost;

	@Value("${SERVER_PORT:9393}")
	protected int serverPort;

	@Value("${APP_LOG_DIR:.}")
	protected String appLogDir;

	@Value("${PLATFORM_TYPE:local}")
	protected String platformType;

	@Value("${BINDER:RABBIT}")
	protected String binder;

	protected RestTemplate restTemplate;

	private StreamOperations streamOperations;

	private AppRegistryOperations appRegistryOperations;

	private List<Stream> streams = new ArrayList<>();

	private static final Logger logger =
			LoggerFactory.getLogger(AbstractStreamTests.class);

	private HostHelper hostHelper;

	/**
	 * A TestWatcher that will write the logs for the failed apps in the
	 * streams that were registered. Also destroys all streams regardless if
	 * the test was successful or failed.
	 */
	@Rule
	public TestWatcher testResultHandler = new TestWatcher() {
		@Override
		protected void failed(Throwable e, Description description) {
			logger.warn(">>>>>>>>>>Test Failed Dumping App Logs<<<<<<<<<");
			for (Stream stream : streams) {
				if (stream.getSink() != null) {
					logger.warn(">>>>>>>> Dumping Sink Log <<<<<<<<<");
					try {
						logger.warn(getLog(stream.getSink()));
					}
					catch (Exception exception) {
						logger.warn("Failed to get log for sink because: ", exception);
					}
				}
				for (String key : stream.getProcessors().keySet()) {
					logger.warn(">>>>>>>> Dumping Processor Log <<<<<<<<<");
					try {
						logger.warn(getLog(stream.getProcessors().get(key)));
					}
					catch (Exception exception) {
						logger.warn("Failed to get log for processor because: ", exception);
					}
				}
				if (stream.getSource() != null) {
					logger.warn(">>>>>>>> Dumping Source Log <<<<<<<<<");
					try {
						logger.warn(getLog(stream.getSource()));
					}
					catch (Exception exception) {
						logger.warn("Failed to get log for source because: ", exception);
					}
				}
			}
			logger.warn(">>>>> App Log Dump Complete <<<<<<");
			destroyStreams();
		}

		@Override
		protected void succeeded(Description description) {
			destroyStreams();
		}
	};

	@Before
	public void setup() {
		registerApps();
	}

	/**
	 * Creates the stream and app operations as well as establish the host helper
	 * that will be used for the acceptance test.
	 */
	public void afterPropertiesSet() {
		if (restTemplate == null) {
			try {
				DataFlowTemplate dataFlowOperationsTemplate =
						new DataFlowTemplate(new URI(serverHost + ":" + serverPort));
				streamOperations = dataFlowOperationsTemplate.streamOperations();
				appRegistryOperations =
						dataFlowOperationsTemplate.appRegistryOperations();
			}
			catch (URISyntaxException uriException) {
				throw new IllegalStateException(uriException);
			}
			restTemplate = new RestTemplate();

			if (platformType.equals(LOCAL_PLATFORM)) {
				hostHelper = new LocalHostHelper(serverHost);
			}
		}
	}

	/**
	 * Destroys all streams registered with the Spring Cloud Data Flow instance.
	 */
	protected void destroyStreams() {
		streamOperations.destroyAll();
		streams.clear();
	}

	/**
	 * Deploys the stream specified to the Spring Cloud Data Flow instance.
	 * @param stream the stream object containing the stream definition.
	 */
	protected void deployStream(Stream stream) {
		streamOperations.createStream(stream.getStreamName(), stream.getDefinition(), true);
		streamAvailable(stream.getStreamName());
		if(stream.getSource() != null) {
			stream.getSource().setHost(
					hostHelper.hostForApplication(stream.getStreamName(),
							stream.getSource().getDefinition()));
		}
		for(Application processor : stream.getProcessors().values()) {
			processor.setHost(
					hostHelper.hostForApplication(stream.getStreamName(),
							processor.getDefinition()));
		}
		if(stream.getSink() != null) {
			stream.getSink().setHost(
					hostHelper.hostForApplication(stream.getStreamName(),
							stream.getSink().getDefinition()));
		}
	}

	/**
	 * Imports the proper apps required for the acceptance tests based on
	 * which binder has been selected (RABBIT, KAFKA).
	 */
	protected void registerApps() {
		if(binder.equals(RABBIT_BINDER)) {
			appRegistryOperations.importFromResource(
					"http://bit.ly/stream-applications-rabbit-maven", true);
		}
		else if (binder.equals(KAFKA_BINDER)) {
			appRegistryOperations.importFromResource(
					"http://bit.ly/stream-applications-kafka-maven", true);
		}
		else {
			throw new IllegalStateException(String.format(
					"Binder type of \"%s\" is invalid.   Only valid types are "
					+ "\"%s\" and \"%s\" ", binder, RABBIT_BINDER,
					KAFKA_BINDER));

		}
	}

	/**
	 * Waits for the stream to be deployed and once deployed the function
	 * returns control else it throws an IllegalStateException.
	 * @param streamName the name of the stream that is to be monitored.
	 */
	protected void streamAvailable(String streamName) {
		boolean isStarted = false;
		int attempt = 0;
		String status = "not present";
		while (!isStarted && attempt < deployPauseRetries) {
			Iterator<StreamDefinitionResource> streamIter =
					streamOperations.list().getContent().iterator();
			StreamDefinitionResource resource = null;
			while (streamIter.hasNext()) {
				resource = streamIter.next();
				if (resource.getName().equals(streamName)) {
					status = resource.getStatus();
					if (status.equals("deployed")) {
						isStarted = true;
					}
					break;
				}
			}
			System.out.println(String.format("Waiting for stream to start " +
					"current status is %s:  " +
					"Attempt %s of %s", status, attempt, deployPauseRetries));
			attempt++;
			waitForDeployment();
		}
		if (!isStarted) {
			throw new IllegalStateException("Unable to start app");
		}
	}

	/**
	 * Creates and initializes a stream object.  Also adds the stream to a
	 * list of streams that will be dumped to logs if the acceptance test fails.
	 * @param streamName the name of the stream to create.
	 * @return initialized stream instance.
	 */
	protected Stream getStream(String streamName) {
		Stream stream = new Stream(streamName, appLogDir);
		streams.add(stream);
		return stream;
	}

	/**
	 * Pauses the run to for a period of seconds as specified by the the
	 * deployPauseTime attribute.
	 */
	protected void waitForDeployment() {
		waitForMillis(deployPauseTime * 1000);
	}

	/**
	 * Pause for the specified time of millis.
	 * @param millis the amount of time to wait in millis.
	 */
	protected void waitForMillis(int millis) {
		try {
			Thread.sleep(millis);
		}
		catch (Exception ex) {
			// ignore
		}
	}

	/**
	 * Retrieve the log for an app.
	 * @param app the app to query to retrieve the log.
	 * @return String containing the contents of the log.
	 */
	protected String getLog(Application app) {
		String logFileUrl =
				String.format("%s:%d/logfile", app.getHost(), app.getPort());
		return restTemplate.getForObject(
				logFileUrl, String.class);
	}

	/**
	 * Post data to the app specified.
	 * @param app the app that will receive the data.
	 * @param port that port the app is monitoring.
	 * @param message the data to be sent to the app.
	 */
	protected void httpPostData(Application app, int port, String message) {
		restTemplate.postForObject(
				String.format("%s:%d", app.getHost(), port),
				message, String.class);
	}

	/**
	 * Waits the specified period of time for an entry to appear in the log of
	 * the specified app.
	 * @param waitTime the time in seconds to wait for an entry to appear in the
	 * log.
	 * @param app the app that is being monitored for a specific entry in its log.
	 * @param entry the value being monitored for in the log.
	 * @return
	 */
	protected boolean waitForLogEntry(int waitTime, Application app,
			String entry) {
		long timeout = System.currentTimeMillis() + (waitTime * 1000);
		boolean exists = false;
		while (!exists && System.currentTimeMillis() < timeout) {
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}
			exists = getLog(app).contains(entry);
		}
		return exists;
	}

}
