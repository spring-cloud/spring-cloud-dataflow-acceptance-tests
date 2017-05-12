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
 */

package org.springframework.cloud.dataflow.acceptance.test.util;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties that are required by the acceptance tests to connect to
 * a Spring Cloud Data Flow Server and the associated binder.
 *
 * @author Glenn Renfro
 */
@ConfigurationProperties
public class TestConfigurationProperties {

	private int maxWaitTime = 30;

	private String binder = "RABBIT";

	private int deployPauseRetries = 25;

	private int deployPauseTime = 5;

	private String serverUri = "http://localhost:9393";

	private String platformType = "LOCAL";

	private String platformSuffix = "local.pcfdev.io";

	private String registrationResource;

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

	public String getRegistrationResource() {
		return registrationResource;
	}

	public void setRegistrationResource(String registrationResource) {
		this.registrationResource = registrationResource;
	}
}
