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
import org.springframework.cloud.dataflow.rest.resource.AppInstanceStatusResource;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;

/**
 * @author Glenn Renfro
 * @author Thomas Risberg
 */
public class LocalPlatformHelper implements PlatformHelper {

	private final String URL = "url";

	private RuntimeOperations operations;

	public LocalPlatformHelper(RuntimeOperations operations) {
		this.operations = operations;
	}

	@Override
	public void setUrisForStream(Stream stream) {
		if(stream.getSource() != null) {
			setAppUri(stream.getStreamName(), stream.getSource());
		}
		if(stream.getSink() != null) {
			setAppUri(stream.getStreamName(), stream.getSink());
		}
		for (Application processor : stream.getProcessors().values()) {
			setAppUri(stream.getStreamName(), processor);
		}
	}

	@Override
	public String getLogfileName() {
		return "${PID}";
	}

	private void setAppUri(String streamName, Application application) {
		Iterator<AppStatusResource> statsIterator = operations.status().iterator();
		AppStatusResource appStatus;
		while (statsIterator.hasNext()) {
			appStatus = statsIterator.next();
			if (appStatus.getDeploymentId().contains(streamName)
					&& appStatus.getDeploymentId().contains((application.getDefinition()))){
				Iterator<AppInstanceStatusResource> resourceIterator =
						appStatus.getInstances().iterator();
				while (resourceIterator.hasNext()) {
					AppInstanceStatusResource resource = resourceIterator.next();
					if (resource.getAttributes().containsKey(URL)) {
						application.setUri(resource.getAttributes().get(URL));
						break;
					}
				}
				break;
			}
		}
	}


}
