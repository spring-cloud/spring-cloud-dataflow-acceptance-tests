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

import java.util.Iterator;

import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;

/**
 * @author Glenn Renfro
 * @author Thomas Risberg
 */
public class CloudFoundryPlatformHelper implements PlatformHelper {

	private RuntimeOperations operations;

	private String cfSuffix;

	public CloudFoundryPlatformHelper(RuntimeOperations operations, String cfSuffix) {
		this.operations = operations;
		this.cfSuffix = cfSuffix;
	}

	@Override
	public void setUrisForStream(StreamDefinition stream) {
		Iterator<AppStatusResource> statsIterator = operations.status().iterator();
		while (statsIterator.hasNext()) {
			AppStatusResource appStatus = statsIterator.next();
			for(Application application : stream.getApplications()){
				setUriForApplication(stream.getName(),cfSuffix,application,appStatus);
			}
		}
	}

	@Override
	public String getLogfileName() {
		return "test.log";
	}

	private void setUriForApplication(String streamName, String cfSuffix,
			Application application, AppStatusResource appStatus) {
		if (application != null
				&& appStatus.getDeploymentId().contains(streamName)
				&& appStatus.getDeploymentId().contains(getAppName(application.getDefinition()))) {
			application.setUri(String.format("http://%s.%s",
					appStatus.getDeploymentId(), cfSuffix));
		}
	}

	private String getAppName(String definition) {
		definition = definition.trim();
		if (definition.contains(" ")) {
			definition = definition.substring(0, definition.indexOf(" "));
		}
		return definition;
	}
}
