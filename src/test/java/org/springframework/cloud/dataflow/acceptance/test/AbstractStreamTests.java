/*
 * Copyright 2016-2017 the original author or authors.
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
import java.util.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.acceptance.test.util.*;
import org.springframework.cloud.dataflow.rest.client.AppRegistryOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.client.StreamOperations;
import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Abstract base class that is used by stream acceptance tests.  This class
 * contains commonly used utility methods for acceptance tests as well as the
 * ability to dump logs of apps when a stream acceptance test fails.
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author Vinicius Carvalho
 */
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(TestConfigurationProperties.class)
public abstract class AbstractStreamTests implements InitializingBean {

	public enum PlatformTypes {
		LOCAL("local"), CLOUDFOUNDRY("cloudfoundry");

		PlatformTypes(String value) {
			this.value = value;
		}

		private String value;

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return value;
		}
	}

	private static final String RABBIT_BINDER = "RABBIT";

	private static final String KAFKA_BINDER = "KAFKA";

	@Autowired
	TestConfigurationProperties configurationProperties;

	protected RestTemplate restTemplate;

	private StreamOperations streamOperations;

	private AppRegistryOperations appRegistryOperations;

	private RuntimeOperations runtimeOperations;

	private List<StreamDefinition> streams = new ArrayList<>();

	private static final Logger logger =
			LoggerFactory.getLogger(AbstractStreamTests.class);

	private PlatformHelper platformHelper;

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
			for (StreamDefinition stream : streams) {
				for(Application application : stream.getApplications()){
					getLog(application);
				}
			}
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
	 * Creates the stream and app operations as well as establish the uri helper
	 * that will be used for the acceptance test.
	 */
	public void afterPropertiesSet() {
		if (restTemplate == null) {
			try {
				DataFlowTemplate dataFlowOperationsTemplate =
						new DataFlowTemplate(new URI(
								configurationProperties.getServerUri()));
				streamOperations = dataFlowOperationsTemplate.streamOperations();
				runtimeOperations = dataFlowOperationsTemplate.runtimeOperations();
				appRegistryOperations =
						dataFlowOperationsTemplate.appRegistryOperations();
				if (configurationProperties.getPlatformType().
						equals(PlatformTypes.LOCAL.getValue())) {
					platformHelper = new LocalPlatformHelper(runtimeOperations);
				}
				if (configurationProperties.getPlatformType().
						equals(PlatformTypes.CLOUDFOUNDRY.getValue())) {
					platformHelper = new CloudFoundryPlatformHelper(runtimeOperations,
							configurationProperties.getPlatformSuffix());
				}
			}
			catch (URISyntaxException uriException) {
				throw new IllegalStateException(uriException);
			}
			restTemplate = new RestTemplate();

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
	protected void deployStream(StreamDefinition stream) {
		logger.info("Deploying stream '" + stream.getName() + "'");
		streamOperations.createStream(stream.getName(),stream.getDefinition(), false);
		Map<String, String> streamProperties = new HashMap<>();
		streamProperties.put("app.*.logging.file", platformHelper.getLogfileName());
		streamProperties.put("app.*.endpoints.logfile.sensitive", "false");

		streamProperties.putAll(stream.getDeploymentProperties());
		streamOperations.deploy(stream.getName(), streamProperties);
		streamAvailable(stream.getName());
		platformHelper.setUrisForStream(stream);
	}

	protected void registerApps() {
		logger.info(String.format("Importing stream apps from uri resource: %s",configurationProperties.getStreamRegistrationResource()));
		appRegistryOperations.importFromResource(configurationProperties.getStreamRegistrationResource(), true);
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
		while (!isStarted && attempt <
				configurationProperties.getDeployPauseRetries()) {
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
			logger.info(String.format("Waiting for stream to start " +
					"current status is %s:  " +
					"Attempt %s of %s", status, attempt,
					configurationProperties.getDeployPauseRetries()));
			attempt++;
			deploymentPause();
		}
		if (!isStarted) {
			throw new IllegalStateException("Unable to start app");
		}
	}



	/**
	 * Pauses the run to for a period of seconds as specified by the the
	 * deployPauseTime attribute.
	 */
	private void deploymentPause() {
		try {
			Thread.sleep(configurationProperties.getDeployPauseTime() * 1000);
		}
		catch (Exception ex) {
			// ignore
		}
	}

	/**
	 * Retrieve the log for an app.
	 * @param app the app to query to retrieve the log.
	 * @return String containing the contents of the log or 'null' if not found.
	 */
	protected String getLog(Application app) {
		String logFileUrl = String.format("%s/logfile", app.getUri());
		String log = null;
		try {
			log = restTemplate.getForObject(logFileUrl, String.class);
		} catch (HttpClientErrorException e) {}
		return log;
	}

	/**
	 * Post data to the app specified.
	 * @param app the app that will receive the data.
	 * @param message the data to be sent to the app.
	 */
	protected void httpPostData(Application app, String message)
			throws URISyntaxException {
		restTemplate.postForObject(
				String.format(app.getUri()),
				message, String.class);
	}

	/**
	 * Waits the specified period of time for an entry to appear in the logfile of
	 * the specified app.
	 * @param app the app that is being monitored for a specific entry.
	 * @param entry the value being monitored for.
	 * @return
	 */
	protected boolean waitForLogEntry(Application app, String entry) {
		logger.info("Looking for '" + entry + "' in logfile for " + app.getDefinition());
		long timeout = System.currentTimeMillis() + (
				configurationProperties.getMaxWaitTime() * 1000);
		boolean exists = false;
		while (!exists && System.currentTimeMillis() < timeout) {
			try {
				Thread.sleep(configurationProperties.getDeployPauseTime() * 1000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}
			String log = getLog(app);
			if (log != null) {
				exists = log.contains(entry);
			}
		}
		if (exists) {
			logger.info("Matched '" + entry + "' in logfile for " +app.getDefinition());
		}
		else {
			logger.error("ERROR: Couldn't find '" + entry + "' in logfile for " +app.getDefinition());
		}
		return exists;
	}

}
