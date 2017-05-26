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
 */

package org.springframework.cloud.dataflow.acceptance.test.util;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotNull;

/**
 * Properties that are required by the acceptance tests to connect to
 * a Spring Cloud Data Flow Server and the associated binder.
 *
 * @author Glenn Renfro
 * @author Thomas Risberg
 */
@ConfigurationProperties
public class TestConfigurationProperties {

	private int maxWaitTime = 120;

	private String binder = "RABBIT";

	private int deployPauseRetries = 30;

	private int deployPauseTime = 5;

	private String serverUri = "http://localhost:9393";

	private String platformType = "local";

	private String platformSuffix = "local.pcfdev.io";

	private String streamRegistrationResource = "http://bit.ly/Bacon-BUILD-SNAPSHOT-stream-applications-rabbit-maven";

	private String taskRegistrationResource = "http://bit.ly/Belmont-BUILD-SNAPSHOT-task-applications-maven";

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
}
