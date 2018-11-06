/*
 * Copyright 2017-2018 the original author or authors.
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.acceptance.test.util.Application;
import org.springframework.cloud.dataflow.acceptance.test.util.DefaultPlatformHelper;
import org.springframework.cloud.dataflow.acceptance.test.util.KubernetesPlatformHelper;
import org.springframework.cloud.dataflow.acceptance.test.util.LocalPlatformHelper;
import org.springframework.cloud.dataflow.acceptance.test.util.LogTestNameRule;
import org.springframework.cloud.dataflow.acceptance.test.util.PlatformHelper;
import org.springframework.cloud.dataflow.acceptance.test.util.StreamDefinition;
import org.springframework.cloud.dataflow.acceptance.test.util.TestConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.AppRegistryOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.client.StreamOperations;
import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * @author Christian Tzolov
 */
@SpringBootTest(classes = { RedisTestConfiguration.class, RedisAutoConfiguration.class })
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(TestConfigurationProperties.class)
public abstract class AbstractStreamTests implements InitializingBean {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static boolean appsRegistered = false;

	@Rule
	public LogTestNameRule logTestName = new LogTestNameRule();

	protected RestTemplate restTemplate;

	@Autowired
	protected TestConfigurationProperties configurationProperties;

	private DataFlowOperations dataFlowOperations;

	private StreamOperations streamOperations;

	private AppRegistryOperations appRegistryOperations;

	private RuntimeOperations runtimeOperations;

	private PlatformHelper platformHelper;

	/**
	 * Ensures that all streams are destroyed regardless if the test was successful or failed.
	 */
	@Rule
	public ExternalResource resourceCleaner = new ExternalResource() {
		@Override
		protected void after() {
			if (streamOperations != null) {
				logger.info("Destroy all streams");
				streamOperations.destroyAll();
			}
		}
	};

	@Before
	public void setup() {
		if (appsRegistered) {
			return;
		}
		registerApps();
	}

	/**
	 * Creates the stream and app operations as well as establish the uri helper that will be
	 * used for the acceptance test.
	 */
	public void afterPropertiesSet() {
		if (restTemplate == null) {
			try {
				dataFlowOperations = new DataFlowTemplate(new URI(
						configurationProperties.getServerUri()));
				streamOperations = dataFlowOperations.streamOperations();
				runtimeOperations = dataFlowOperations.runtimeOperations();
				appRegistryOperations = dataFlowOperations.appRegistryOperations();
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
	 * Deploys the stream specified to the Spring Cloud Data Flow instance.
	 * @param stream the stream object containing the stream definition.
	 */
	protected void deployStream(StreamDefinition stream) {
		logger.info("Creating stream '" + stream.getName() + "'");
		streamOperations.createStream(stream.getName(), stream.getDefinition(), false);
		Map<String, String> streamProperties = new HashMap<>();
		streamProperties.put("app.*.logging.file", platformHelper.getLogfileName());
		streamProperties.put("app.*.endpoints.logfile.sensitive", "false");

		// Specific to Boot 2.x applications, also allows access without authentication
		streamProperties.put("app.*.management.endpoints.web.exposure.include", "*");

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
		appsRegistered = true;
	}

	/**
	 * Waits for the stream to be deployed and once deployed the function returns control else
	 * it throws an IllegalStateException.
	 * @param stream the stream that is to be monitored.
	 */
	protected void streamAvailable(StreamDefinition stream) {
		boolean streamStarted = false;
		int attempt = 0;
		String status = "not present";
		StreamDefinitionResource resource = null;
		while (!streamStarted && attempt < configurationProperties.getDeployPauseRetries()) {
			Iterator<StreamDefinitionResource> streamIter = streamOperations.list().getContent().iterator();
			while (streamIter.hasNext()) {
				resource = streamIter.next();
				if (resource.getName().equals(stream.getName())) {
					status = resource.getStatus();
					logger.info("Checking: Stream=" + stream.getName() +
							", status = " + status +
							", status description = " + resource.getStatusDescription());
					if (status.equals("deployed")) {
						boolean urlsAvailable = platformHelper.setUrlsForStream(stream);
						if (urlsAvailable) {
							streamStarted = true;
						}
					}
					break;
				}
			}
			attempt++;
			logger.info("Sleeping to check status of Stream=" + stream.getName());
			deploymentPause();
		}
		if (streamStarted) {
			logger.info(String.format("Stream '%s' started with status: %s", stream.getName(), status));
			for (Application app : stream.getApplications()) {
				logger.info("App '" + app.getName() + "' has instances: " + app.getInstanceUrls());
			}
		}
		else {
			String statusDescription = "null";
			if (resource != null) {
				statusDescription = resource.getStatusDescription();
			}
			logger.info(String.format("Stream '%s' NOT started with status: %s.  Description = %s",
					stream.getName(), status, statusDescription));

			throw new IllegalStateException("Unable to start stream " + stream.getName() +
					".  Definition = " + stream.getDefinition());
		}
	}

	/**
	 * Pauses the run to for a period of seconds as specified by the the deployPauseTime
	 * attribute.
	 */
	protected void deploymentPause() {
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

		String logFileUrl = getLogFileUrl(url);
		String log = null;
		try {
			log = restTemplate.getForObject(logFileUrl, String.class);
			if (log == null) {
				logger.info("Unable to retrieve logfile from '" + logFileUrl);
			}
			else {
				logger.info("Retrieved logfile from '" + logFileUrl);
			}
		}
		catch (HttpClientErrorException e) {
			logger.info("Failed to access logfile from '" + logFileUrl + "' due to : " + e.getMessage());
		}
		catch (Exception e) {
			logger.warn("Error while trying to access logfile from '" + logFileUrl + "' due to : " + e);
		}
		return log;
	}

	private String getLogFileUrl(String url) {
		String logFileUrl = null;
		// check if this is a boot 2.x application, and if so, follow 2.x url conventions to access log file.
		String actuatorUrl = String.format("%s/actuator", url);
		ResponseEntity<String> responseEntity = restTemplate.exchange(actuatorUrl, HttpMethod.GET, null, String.class);
		if (responseEntity.getStatusCode() == HttpStatus.OK) {
			logFileUrl = String.format("%s/actuator/logfile", url);
		} else {
			logFileUrl = String.format("%s/logfile", url);
		}
		return logFileUrl;
	}

	/**
	 * Post data to the app specified.
	 * @param app the app that will receive the data.
	 * @param message the data to be sent to the app.
	 */
	protected void httpPostData(Application app, String message) {
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
		logger.info("Looking for '" + StringUtils.arrayToCommaDelimitedString(entries) + "' in logfile for "
				+ app.getDefinition());
		final long timeout = System.currentTimeMillis() + (configurationProperties.getMaxWaitTime() * 1000);
		boolean exists = false;
		String instance = "?";
		Map<String, String> logData = new HashMap<>();

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
					logger.info("Requesting log for app " + appInstance);
					String log = getLog(app.getInstanceUrls().get(appInstance));
					logData.put(appInstance, log);
					if (log != null) {
						if (Stream.of(entries).allMatch(log::contains)) {
							exists = true;
							instance = appInstance;
						}
					}
					else {
						logger.info("Polling to get log file. Remaining poll time = "
								+ Long.toString((timeout - System.currentTimeMillis()) / 1000) + " seconds.");
					}
				}
			}
		}
		if (exists) {
			logger.info("Matched all '" + StringUtils.arrayToCommaDelimitedString(entries)
					+ "' in logfile for instance " + instance + " of app " + app.getDefinition());
		}
		else {
			logger.error("ERROR: Couldn't find '" + StringUtils.arrayToCommaDelimitedString(entries)
					+ "' in logfiles for " + app.getDefinition() + ". Dumping log files.\n\n");
			for (Map.Entry<String, String> entry : logData.entrySet()) {
				logger.error("<logFile> =================");
				logger.error("Log File for " + entry.getValue() + "\n" + entry.getValue());
				logger.error("</logFile> ================\n");
			}
		}
		return exists;
	}

	protected DataFlowOperations dataFlowOperations() {
		return dataFlowOperations;
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
