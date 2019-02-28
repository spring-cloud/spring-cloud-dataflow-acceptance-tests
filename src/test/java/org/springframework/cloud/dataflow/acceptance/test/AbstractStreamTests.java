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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
import org.springframework.cloud.dataflow.rest.util.DeploymentPropertiesUtils;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
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
 * @author Ilayaperumal Gopinathan
 * @author Chris Cheetham
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

	protected DataFlowOperations dataFlowOperations;

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
				if (isKubernetesPlatform()) {
					platformHelper = new KubernetesPlatformHelper(runtimeOperations);
				}
				else if (isLocalPlatform()) {
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

	/**
	 * Updates the stream specified to the Spring Cloud Data Flow instance.
	 * @param stream the stream object containing the stream definition.
	 * @return the available StreamDefinition REST resource.
	 */
	protected StreamDefinitionResource updateStream(StreamDefinition stream, String properties, List<String> appNames) {
		Map<String, String> propertiesToUse = null;
		try {
			propertiesToUse = DeploymentPropertiesUtils.parseDeploymentProperties(properties,
					null, 0);
		}
		catch(IOException e) {
			throw new RuntimeException(e.getMessage());
		}
		String streamName = stream.getName();
		PackageIdentifier packageIdentifier = new PackageIdentifier();
		packageIdentifier.setPackageName(stream.getName());
		logger.info("Updating stream '" + stream.getName() + "'");
		this.streamOperations.updateStream(streamName, streamName, packageIdentifier, propertiesToUse, false, appNames);
		return streamAvailable(stream);
	}

	/**
	 * Rollback the stream to a specific version.
	 * @param stream the stream object containing the stream definition.
	 * @return the available StreamDefinition REST resource.
	 */
	protected StreamDefinitionResource rollbackStream(StreamDefinition stream) {
		return this.rollbackStream(stream, 0);
	}

	/**
	 * Rollback the stream to a specific version.
	 * @param stream the stream object containing the stream definition.
	 * @param streamVersion the stream version to rollback to
	 * @return the available StreamDefinition REST resource.
	 */
	protected StreamDefinitionResource rollbackStream(StreamDefinition stream, int streamVersion) {
		logger.info("Rolling back the stream '" + stream.getName() + "' to the version "+ streamVersion);
		this.streamOperations.rollbackStream(stream.getName(), streamVersion);
		return streamAvailable(stream);
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
	 * @return the available StreamDefinition REST resource.
	 */
	protected StreamDefinitionResource streamAvailable(StreamDefinition stream) {
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
		return resource;
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
		ResponseEntity<String> responseEntity = null;
		try {
			responseEntity = restTemplate.exchange(actuatorUrl, HttpMethod.GET, null, String.class);
		} catch (Exception e) {

		}
		if (responseEntity != null && responseEntity.getStatusCode() == HttpStatus.OK) {
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
		List<Log> logs = null;
		LogRetriever logRetriever = isCloudFoundry() ? new CloudFoundryLogRetriever(app) : new DefaultLogRetriever(app);

		while (System.currentTimeMillis() < timeout) {
			try {
				Thread.sleep(configurationProperties.getDeployPauseTime() * 1000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}
			try {
				logs = logRetriever.retrieveLogs();
				for (Log log : logs) {
					if (log.content != null && Stream.of(entries).allMatch(log.content::contains)) {
						logger.info("Matched all '" + StringUtils.arrayToCommaDelimitedString(entries)
								+ "' in logfile for app " + app.getDefinition() + " (source " + log.source + ")");
						return true;
					}
				}
			}
			catch (IOException e) {
				logger.error("ERROR: " + e.getMessage());
			}
			logger.info("Polling to get log file. Remaining poll time = "
					+ Long.toString((timeout - System.currentTimeMillis()) / 1000) + " seconds.");
		}

		logger.error("ERROR: Couldn't find '" + StringUtils.arrayToCommaDelimitedString(entries) + "; details below");
		if (logs == null || logs.size() == 0) {
			logger.error("ERROR: No logs retrieved");
		}
		else {
			logger.error("ERROR: Dumping most recent log files.\n\n");
			for (Log log : logs) {
				logger.error("<logFile> =================");
				logger.error("Log File for " + log.source + "\n" + log.content);
				logger.error("</logFile> ================\n");
			}
		}
		return false;
	}

	protected DataFlowOperations dataFlowOperations() {
		return dataFlowOperations;
	}

	private boolean isLocalPlatform() {
		return configurationProperties.getPlatformType().equals(PlatformTypes.LOCAL.getValue());
	}

	private boolean isCloudFoundry() {
		return configurationProperties.getPlatformType().equals(PlatformTypes.CLOUDFOUNDRY.getValue());
	}

	private boolean isKubernetesPlatform() {
		return configurationProperties.getPlatformType().equals(PlatformTypes.GKE.getValue()) ||
				configurationProperties.getPlatformType().equals(PlatformTypes.PKS.getValue());
	}

	public enum PlatformTypes {
		LOCAL("local"),
		CLOUDFOUNDRY("cloudfoundry"),
		GKE("gke"),
		PKS("pks");

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

	class Log {
		String source;
		String content;
	}

	private abstract class LogRetriever {

		final Application app;

		LogRetriever(Application app) {
			this.app = app;
		}

		abstract List<Log> retrieveLogs() throws IOException;
	}

	private class DefaultLogRetriever extends LogRetriever {

		public DefaultLogRetriever(Application app) {
			super(app);
		}

		@Override
		List<Log> retrieveLogs() {
		    List<Log> logs = new LinkedList<>();
			for (String appInstance : app.getInstanceUrls().keySet()) {
				logger.info("Requesting log for app " + appInstance);
				Log log = new Log();
				log.source = appInstance;
				log.content = getLog(app.getInstanceUrls().get(appInstance));
				logs.add(log);
			}
			return logs;
		}
	}

	private class CloudFoundryLogRetriever extends LogRetriever {

		public CloudFoundryLogRetriever(Application app) {
			super(app);
		}

		@Override
		List<Log> retrieveLogs() throws IOException {
			final int maxWait = configurationProperties.getMaxWaitTime();
			Log log = new Log();
			try {
				log.source = new URL(app.getUrl()).getHost().split("\\.")[0];
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("Malformed url: " + app.getUrl(), e);
			}
			String[] cfCommand = {"cf", "logs", "--recent", log.source};
			logger.info("Running system command: " + String.join(" ", cfCommand));
			ProcessBuilder procBuilder = new ProcessBuilder(cfCommand);
			Process proc = procBuilder.start();
			boolean exited;
			try {
				exited = proc.waitFor(maxWait, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}
			if (exited) {
				int rc = proc.exitValue();
				if (rc == 0) {
					log.content = readStringFromInputStream(proc.getInputStream());
				} else {
					logger.error("ERROR: running system command [rc=" + rc + "]: " + readStringFromInputStream(proc.getErrorStream()));
				}
			} else {
				logger.error("ERROR: system command exceeded maximum wait time (" + maxWait + "s)");
			}
			List<Log> logs = new LinkedList<>();
			logs.add(log);
			return logs;
		}

		private String readStringFromInputStream(InputStream input) throws IOException {
		    final String newline = System.getProperty("line.separator");
			try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
				return buffer.lines().collect(Collectors.joining(newline));
			}
		}
	}

}
