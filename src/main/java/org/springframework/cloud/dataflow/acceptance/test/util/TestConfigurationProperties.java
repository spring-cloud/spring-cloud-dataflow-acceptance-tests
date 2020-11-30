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

package org.springframework.cloud.dataflow.acceptance.test.util;

import java.util.Properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties that are required by the acceptance tests to connect to a Spring Cloud Data
 * Flow Server and the associated binder.
 *
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author David Turanski
 */
@ConfigurationProperties
public class TestConfigurationProperties {

	private int maxWaitTime = 300;

	private String binder = "RABBIT";

	private int deployPauseRetries = 60;

	private int deployPauseTime = 5;

	private String serverUri = "http://localhost:9393";

	private String platformType = "local";

	private String platformSuffix = "local.pcfdev.io";

	private String dataflowServiceAccountName;

	private String namespace="default";

	private String streamRegistrationResource = "https://https://dataflow.spring.io/rabbitmq-maven-latest";

	private String taskRegistrationResource = "https://dataflow.spring.io/task-maven-latest";

	private Properties taskRegistrationProperties; //Arrays.asList("task.scenario=maven://io.spring:scenario-task:0.0.1-SNAPSHOT");

	private String appHost;

	private boolean useHttps;

	public TestConfigurationProperties() {
		this.taskRegistrationProperties = new Properties();
		this.taskRegistrationProperties.setProperty("task.scenario", "maven://io.spring:scenario-task:0.0.1-SNAPSHOT");
	}

	public int getMaxWaitTime() {
		return maxWaitTime;
	}

	public void setMaxWaitTime(int maxWaitTime) {
		this.maxWaitTime = maxWaitTime;
	}

	public String getBinder() {
		return binder;
	}

	public void setBinder(String binder) {
		this.binder = binder;
	}

	public int getDeployPauseRetries() {
		return deployPauseRetries;
	}

	public void setDeployPauseRetries(int deployPauseRetries) {
		this.deployPauseRetries = deployPauseRetries;
	}

	public int getDeployPauseTime() {
		return deployPauseTime;
	}

	public void setDeployPauseTime(int deployPauseTime) {
		this.deployPauseTime = deployPauseTime;
	}

	public String getServerUri() {
		return serverUri;
	}

	public void setServerUri(String serverUri) {
		this.serverUri = serverUri;
	}

	public String getPlatformType() {
		return platformType;
	}

	public void setPlatformType(String platformType) {
		this.platformType = platformType;
	}

	public String getPlatformSuffix() {
		return platformSuffix;
	}

	public void setPlatformSuffix(String platformSuffix) {
		this.platformSuffix = platformSuffix;
	}

	public String getStreamRegistrationResource() {
		return streamRegistrationResource;
	}

	public void setStreamRegistrationResource(String streamRegistrationResource) {
		this.streamRegistrationResource = streamRegistrationResource;
	}

	public String getTaskRegistrationResource() {
		return taskRegistrationResource;
	}

	public void setTaskRegistrationResource(String taskRegistrationResource) {
		this.taskRegistrationResource = taskRegistrationResource;
	}

	public Properties getTaskRegistrationProperties() {
		return taskRegistrationProperties;
	}

	public void setTaskRegistrationProperties(Properties taskRegistrationProperties) {
		this.taskRegistrationProperties = taskRegistrationProperties;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getDataflowServiceAccountName() {
		return dataflowServiceAccountName;
	}

	public void setDataflowServiceAccountName(String dataflowServiceAccountName) {
		this.dataflowServiceAccountName = dataflowServiceAccountName;
	}

	public void setAppHost(String appHost) {
		this.appHost = appHost;
	}

	public String getAppHost() {
		return appHost;
	}

	public boolean isUseHttps() {
		return useHttps;
	}

	public void setUseHttps(boolean useHttps) {
		this.useHttps = useHttps;
	}
}
