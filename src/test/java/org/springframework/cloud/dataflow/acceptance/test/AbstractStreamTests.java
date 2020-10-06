/*
 * Copyright 2017-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
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
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.acceptance.test.util.Application;
import org.springframework.cloud.dataflow.acceptance.test.util.DataFlowTemplateBuilder;
import org.springframework.cloud.dataflow.acceptance.test.util.DefaultPlatformHelper;
import org.springframework.cloud.dataflow.acceptance.test.util.HttpPoster;
import org.springframework.cloud.dataflow.acceptance.test.util.KubernetesPlatformHelper;
import org.springframework.cloud.dataflow.acceptance.test.util.LocalPlatformHelper;
import org.springframework.cloud.dataflow.acceptance.test.util.LogTestNameRule;
import org.springframework.cloud.dataflow.acceptance.test.util.PlatformHelper;
import org.springframework.cloud.dataflow.acceptance.test.util.RestTemplateConfigurer;
import org.springframework.cloud.dataflow.acceptance.test.util.StreamDefinition;
import org.springframework.cloud.dataflow.acceptance.test.util.TestConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.AppRegistryOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.client.StreamOperations;
import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.cloud.dataflow.rest.util.DeploymentPropertiesUtils;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

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
 * @author David Turanski
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@EnableConfigurationProperties(TestConfigurationProperties.class)
public abstract class AbstractStreamTests implements InitializingBean {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private static boolean appsRegistered = false;

	@Rule
	public LogTestNameRule logTestName = new LogTestNameRule();

	@Autowired
	protected TestConfigurationProperties configurationProperties;

	protected RetryTemplate retryTemplate = new RetryTemplate();

	protected DataFlowOperations dataFlowOperations;

	private StreamOperations streamOperations;

	private AppRegistryOperations appRegistryOperations;

	private RuntimeOperations runtimeOperations;

	private PlatformHelper platformHelper;

	protected RestTemplate restTemplate;

	private HttpPoster httpPoster;

	@Rule
	public LoggingTestWatcher testWatcher = new LoggingTestWatcher();

	class LoggingTestWatcher extends TestWatcher {
		List<LogRetriever> logRetrievers = new LinkedList<>();

		@Override
		protected void failed(Throwable e, Description description) {
			super.failed(e, description);
			logRetrievers.forEach(logRetriever -> {
				logRetriever.retrieveLogs().forEach(log -> logger.error(log.getContent()));
			});
			logger.info("Destroy all streams");
			streamOperations.destroyAll();
		}

		@Override
		protected void succeeded(Description description) {
			logger.info("Destroy all streams");
			streamOperations.destroyAll();
		}
	}

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
		restTemplate = new RestTemplateConfigurer().skipSslValidation(configurationProperties.isUseHttps()).configure();
		dataFlowOperations = DataFlowTemplateBuilder.serverUri(configurationProperties.getServerUri())
				.restTemplate(restTemplate).build();
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
		httpPoster = new HttpPoster(restTemplate);
	}

	/**
	 * Deploys the stream specified to the Spring Cloud Data Flow instance.
	 * @param stream the stream object containing the stream definition.
	 */
	protected void deployStream(StreamDefinition stream) {
		logger.info("Creating stream '" + stream.getName() + "'");
		retryTemplate
				.execute(context -> streamOperations.createStream(stream.getName(), stream.getDefinition(), false));
		Map<String, String> streamProperties = new HashMap<>();
		streamProperties.put("app.*.logging.file", platformHelper.getLogfileName());
		streamProperties.put("app.*.endpoints.logfile.sensitive", "false");

		// Specific to Boot 2.x applications, also allows access without authentication
		streamProperties.put("app.*.management.endpoints.web.exposure.include", "*");

		streamProperties.put("app.*.spring.cloud.streamapp.security.enabled", "false");

		platformHelper.addDeploymentProperties(stream, streamProperties);

		streamProperties.putAll(stream.getDeploymentProperties());
		logger.info("Deploying stream '" + stream.getName() + "' with properties: " + streamProperties);
		retryTemplate.execute(context -> {
			streamOperations.deploy(stream.getName(), streamProperties);
			return null;
		});
		streamAvailable(stream);
	}

	/**
	 * Updates the stream specified to the Spring Cloud Data Flow instance.
	 * @param stream the stream object containing the stream definition.
	 * @return the available StreamDefinition REST resource.
	 */
	protected StreamDefinitionResource updateStream(StreamDefinition stream, String properties, List<String> appNames) {
		final Map<String, String> propertiesToUse = new HashMap<>();
		try {
			propertiesToUse.putAll(DeploymentPropertiesUtils.parseDeploymentProperties(properties,
					null, 0));
		}
		catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
		String streamName = stream.getName();
		PackageIdentifier packageIdentifier = new PackageIdentifier();
		packageIdentifier.setPackageName(stream.getName());
		logger.info("Updating stream '" + stream.getName() + "'");
		retryTemplate.execute(context -> {
			this.streamOperations.updateStream(streamName, streamName, packageIdentifier, propertiesToUse, false,
					appNames);
			return null;
		});
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
		logger.info("Rolling back the stream '" + stream.getName() + "' to the version " + streamVersion);
		retryTemplate.execute(context -> {
			this.streamOperations.rollbackStream(stream.getName(), streamVersion);
			return null;
		});
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
			Iterator<StreamDefinitionResource> streamIter = retryTemplate
					.execute(context -> streamOperations.list().getContent().iterator());
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
			if (!streamStarted) {
				attempt++;
				logger.info("Sleeping to check status of Stream=" + stream.getName());
				deploymentPause();
			}
		}

		if (streamStarted) {
			sleep(Duration.ofSeconds(10));
			logger.info(String.format("Stream '%s' started with status: %s", stream.getName(), status));
			for (Application app : stream.getApplications()) {
				logger.info("App '" + app.getName() + "' has instances: " + app.getInstanceUrls());
				if (app.getInstanceUrls().isEmpty()) {
					throw new IllegalStateException("App '" + app.getName() + "' returned no instances: ");
				}
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
		sleep(Duration.ofSeconds(configurationProperties.getDeployPauseTime()));
	}

	protected void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (Exception ex) {
			Thread.currentThread().interrupt();
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
			log = retryTemplate.execute(context -> restTemplate.getForObject(logFileUrl, String.class));
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
		// check if this is a boot 2.x application, and if so, follow 2.x url conventions to
		// access log file.
		String actuatorUrl = String.format("%s/actuator", url);
		ResponseEntity<String> responseEntity = null;
		try {
			responseEntity = retryTemplate
					.execute(context -> restTemplate.exchange(actuatorUrl, HttpMethod.GET, null, String.class));
		}
		catch (Exception e) {

		}
		if (responseEntity != null && responseEntity.getStatusCode() == HttpStatus.OK) {
			logFileUrl = String.format("%s/actuator/logfile", url);
		}
		else {
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
		UriBuilder uriBuilder = new DefaultUriBuilderFactory(app.getUrl()).builder();
		if (this.configurationProperties.isUseHttps()) {
			uriBuilder.scheme("https");
		}
		retryTemplate.execute(
				context -> {
					httpPoster.httpPostData(uriBuilder.build(), message);
					return null;
				});
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
		List<Log> logs = new ArrayList<>();
		LogRetriever logRetriever = logRetriever(app);
		this.testWatcher.logRetrievers.add(logRetriever);

		while (System.currentTimeMillis() < timeout) {
			deploymentPause();
			logs = logRetriever.retrieveLogs();

			for (Log log : logs) {
				if (log.getContent() != null && Stream.of(entries).allMatch(log.getContent()::contains)) {
					logger.info("Matched all '" + StringUtils.arrayToCommaDelimitedString(entries)
							+ "' in logfile for app " + app.getDefinition() + " (source " + log.getSource() + ")");
					return true;
				}
			}
			logger.info("Polling to get log file. Remaining poll time = "
					+ (timeout - System.currentTimeMillis()) / 1000 + " seconds.");
		}

		logger.error("ERROR: Couldn't find '" + StringUtils.arrayToCommaDelimitedString(entries) + "; details below");
		if (logs.isEmpty()) {
			logger.error("ERROR: No logs retrieved");
		}
		else {
			logger.error("ERROR: Dumping most recent log files.\n\n");
			for (Log log : logs) {
				logger.error("<logFile> =================");
				logger.error("Log File for " + log.getSource() + "\n" + log.getContent());
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
		return configurationProperties.getPlatformType().equals(PlatformTypes.KUBERNETES.getValue());
	}

	private LogRetriever logRetriever(Application application) {
		switch (PlatformTypes.of(configurationProperties.getPlatformType())) {
		case CLOUDFOUNDRY:
			return new CloudFoundryLogRetriever(application);
		case KUBERNETES:
			return new KubernetesLogRetriever(application);
		default:
			return new DefaultLogRetriever(application);
		}
	}

	public enum PlatformTypes {
		LOCAL("local"),
		CLOUDFOUNDRY("cloudfoundry"),
		KUBERNETES("kubernetes");

		private final String value;

		PlatformTypes(String value) {
			this.value = value;
		}

		public final String getValue() {
			return value;
		}

		static PlatformTypes of(String value) {
			return PlatformTypes.valueOf(value.toUpperCase());
		}

		@Override
		public final String toString() {
			return value;
		}
	}

	private class Log {

		private final String source;

		private final String content;

		private Log(String source, String content) {
			this.source = source;
			this.content = content;
		}

		private String getSource() {
			return source;
		}

		private String getContent() {
			return content;
		}
	}

	private abstract class LogRetriever {

		final Application app;

		LogRetriever(Application app) {
			this.app = app;
		}

		abstract List<Log> retrieveLogs();
	}

	private class DefaultLogRetriever extends LogRetriever {

		public DefaultLogRetriever(Application app) {
			super(app);
		}

		@Override
		List<Log> retrieveLogs() {
			List<Log> logs = new ArrayList<>();
			for (String appInstance : app.getInstanceUrls().keySet()) {
				logger.info("Requesting log for app " + appInstance);
				String content = getLog(app.getInstanceUrls().get(appInstance));
				Log log = new Log(appInstance, content);
				logs.add(log);
			}
			return logs;
		}
	}

	private abstract class AbstractCommandLogRetriever extends LogRetriever {
		AbstractCommandLogRetriever(Application app) {
			super(app);
		}

		protected Log doRetrieveLog(String logSource, String... command) {
			String logContent;
			final int maxWaitInSeconds = 30;
			logger.info("Running system command: " + String.join(" ", command));
			ProcessBuilder procBuilder = new ProcessBuilder(command);
			Process proc;
			try {
				proc = procBuilder.start();
			}
			catch (IOException e) {
				throw new IllegalStateException("Can't execute command", e);
			}
			boolean exited;

			try {
				logContent = readStringFromInputStream(proc.getInputStream());
				logger.info("Waiting for command to exit");
				exited = proc.waitFor(maxWaitInSeconds, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}
			if (exited) {
				int rc = proc.exitValue();
				if (rc != 0) {
					logger.error("ERROR: running system command [rc=" + rc + "]: "
							+ readStringFromInputStream(proc.getErrorStream()));
				}
			}
			else {
				logger.error("ERROR: system command exceeded maximum wait time (" + maxWaitInSeconds + "s)");
			}

			return new Log(logSource, logContent);
		}

		private String readStringFromInputStream(InputStream input) {
			final String newline = System.getProperty("line.separator");
			try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
				return buffer.lines().collect(Collectors.joining(newline));
			}
			catch (IOException e) {
				logger.error("ERROR: reading command output: " + e.getMessage());
			}
			return null;
		}
	}

	private class KubernetesLogRetriever extends AbstractCommandLogRetriever {

		KubernetesLogRetriever(Application app) {
			super(app);
		}

		@Override
		List<Log> retrieveLogs() {
			List<Log> logs = new ArrayList<>();
			for (String appInstance : app.getInstanceUrls().keySet()) {
				logs.add(doRetrieveLog(appInstance, "kubectl", "logs", "-n",
						configurationProperties.getNamespace(), appInstance));
			}
			return logs;
		}
	}

	private class CloudFoundryLogRetriever extends AbstractCommandLogRetriever {

		public CloudFoundryLogRetriever(Application app) {
			super(app);
		}

		@Override
		List<Log> retrieveLogs() {
			List<Log> logs = new ArrayList<>();
			String logSource = null;
			try {
				logSource = new URL(app.getUrl()).getHost().split("\\.")[0];
			}
			catch (MalformedURLException e) {
				throw new IllegalArgumentException("Malformed url: " + app.getUrl(), e);
			}

			logs.add(doRetrieveLog(logSource, "cf", "logs", "--recent", logSource));

			return logs;
		}
	}
}
