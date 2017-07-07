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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Abstract base class that is used by stream acceptance tests. This class contains
 * commonly used utility methods for acceptance tests as well as the ability to dump logs
 * of apps when a stream acceptance test fails.
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author Vinicius Carvalho
 */
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(TestConfigurationProperties.class)
public abstract class AbstractStreamTests implements InitializingBean {

	private static final String RABBIT_BINDER = "RABBIT";

	private static final String KAFKA_BINDER = "KAFKA";

	private static final Logger logger = LoggerFactory.getLogger(AbstractStreamTests.class);

	protected RestTemplate restTemplate;

	@Autowired
	TestConfigurationProperties configurationProperties;

	private StreamOperations streamOperations;

	private AppRegistryOperations appRegistryOperations;

	private RuntimeOperations runtimeOperations;

	private List<StreamDefinition> streams = new ArrayList<>();

	/**
	 * A TestWatcher that will write the logs for the failed apps in the streams that were
	 * registered. Also destroys all streams regardless if the test was successful or
	 * failed.
	 */
	@Rule
	public TestWatcher testResultHandler = new TestWatcher() {
		@Override
		protected void failed(Throwable e, Description description) {
			logger.warn(">>>>>>>>>>Test Failed Dumping App Logs<<<<<<<<<");
			for (StreamDefinition stream : streams) {
				for (Application application : stream.getApplications()) {
					getLog(application.getUrl());
				}
			}
			destroyStreams();
		}

		@Override
		protected void succeeded(Description description) {
			destroyStreams();
		}
	};

	private PlatformHelper platformHelper;

	@Before
	public void setup() {
		registerApps();
	}

	/**
	 * Creates the stream and app operations as well as establish the uri helper that will
	 * be used for the acceptance test.
	 */
	public void afterPropertiesSet() {
		if (restTemplate == null) {
			try {
				DataFlowTemplate dataFlowOperationsTemplate = new DataFlowTemplate(new URI(
						configurationProperties.getServerUri()));
				streamOperations = dataFlowOperationsTemplate.streamOperations();
				runtimeOperations = dataFlowOperationsTemplate.runtimeOperations();
				appRegistryOperations = dataFlowOperationsTemplate.appRegistryOperations();
				if (configurationProperties.getPlatformType().equals(PlatformTypes.KUBERNETES.getValue())) {
					platformHelper = new KubernetesPlatformHelper(runtimeOperations);
				}
				else if (configurationProperties.getPlatformType().equals(PlatformTypes.LOCAL.getValue())) {
					platformHelper = new LocalPlatformHelper(runtimeOperations);
				}
				else {
					platformHelper = new DefaultPlatformHelper(runtimeOperations);
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
		logger.info("Creating stream '" + stream.getName() + "'");
		streamOperations.createStream(stream.getName(), stream.getDefinition(), false);
		Map<String, String> streamProperties = new HashMap<>();
		streamProperties.put("app.*.logging.file", platformHelper.getLogfileName());
		streamProperties.put("app.*.endpoints.logfile.sensitive", "false");
		platformHelper.addDeploymentProperties(stream, streamProperties);

		streamProperties.putAll(stream.getDeploymentProperties());
		logger.info("Deploying stream '" + stream.getName() + "' with properties: " + streamProperties);
		streamOperations.deploy(stream.getName(), streamProperties);
		streamAvailable(stream);
	}

	protected void registerApps() {
		logger.info(String.format("Importing stream apps from uri resource: %s",
				configurationProperties.getStreamRegistrationResource()));
		appRegistryOperations.importFromResource(configurationProperties.getStreamRegistrationResource(), true);
	}

	/**
	 * Waits for the stream to be deployed and once deployed the function returns control
	 * else it throws an IllegalStateException.
	 * @param stream the stream that is to be monitored.
	 */
	protected void streamAvailable(StreamDefinition stream) {
		boolean streamStarted = false;
		int attempt = 0;
		String status = "not present";
		while (!streamStarted && attempt < configurationProperties.getDeployPauseRetries()) {
			Iterator<StreamDefinitionResource> streamIter = streamOperations.list().getContent().iterator();
			StreamDefinitionResource resource = null;
			while (streamIter.hasNext()) {
				resource = streamIter.next();
				if (resource.getName().equals(stream.getName())) {
					status = resource.getStatus();
					if (status.equals("deployed")) {
						boolean urlsAvailable = platformHelper.setUrlsForStream(stream);
						if (urlsAvailable) {
							streamStarted = true;
						}
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
		if (!streamStarted) {
			throw new IllegalStateException("Unable to start app");
		}
	}

	/**
	 * Pauses the run to for a period of seconds as specified by the the deployPauseTime
	 * attribute.
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
	 * @param url the app URL to query to retrieve the log.
	 * @return String containing the contents of the log or 'null' if not found.
	 */
	protected String getLog(String url) {
		String logFileUrl = String.format("%s/logfile", url);
		String log = null;
		try {
			log = restTemplate.getForObject(logFileUrl, String.class);
		}
		catch (HttpClientErrorException e) {
			logger.info("Failed to access logfile at '" + logFileUrl + "' due to : " + e.getMessage());
		}
		catch (Exception e) {
			logger.warn("Error while trying to access logfile at '" + logFileUrl + "' due to : " + e);
		}
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
				String.format(app.getUrl()),
				message, String.class);
	}

	/**
	 * Waits the specified period of time for an entry to appear in the logfile of the
	 * specified app.
	 * @param app the app that is being monitored for a specific entry.
	 * @param entries the array of values being monitored for.
	 * @return
	 */
	protected boolean waitForLogEntry(Application app, String... entries) {
		logger.info("Looking for '" + StringUtils.arrayToCommaDelimitedString(entries) + "' in logfile for " + app.getDefinition());
		long timeout = System.currentTimeMillis() + (configurationProperties.getMaxWaitTime() * 1000);
		boolean exists = false;
		String instance = "?";
		while (!exists && System.currentTimeMillis() < timeout) {
			try {
				Thread.sleep(configurationProperties.getDeployPauseTime() * 1000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}
			for (String appInstance : app.getInstanceUrls().keySet()) {
				if (!exists) {
					String log = getLog(app.getInstanceUrls().get(appInstance));
					if (log != null) {
						if (Stream.of(entries).allMatch(s -> log.contains(s))) {
							exists = true;
							instance = appInstance;
						}
					}
				}
			}
		}
		if (exists) {
			logger.info("Matched all '" + StringUtils.arrayToCommaDelimitedString(entries) + "' in logfile for instance " + instance + " of app " + app.getDefinition());
		}
		else {
			logger.error("ERROR: Couldn't find all '" + StringUtils.arrayToCommaDelimitedString(entries) + "' in logfile for " + app.getDefinition());
		}
		return exists;
	}

	public enum PlatformTypes {
		LOCAL("local"),
		CLOUDFOUNDRY("cloudfoundry"),
		KUBERNETES("kubernetes");

		private String value;

		PlatformTypes(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return value;
		}
	}

}
