/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.dataflow.acceptance.test.util;

import java.util.Iterator;
import java.util.Map;

import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.resource.AppInstanceStatusResource;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;

/**
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author Vinicius Carvalho
 */
public abstract class AbstractPlatformHelper implements PlatformHelper {

	protected final String URL = "url";

	protected RuntimeOperations operations;

	public AbstractPlatformHelper(RuntimeOperations operations) {
		this.operations = operations;
	}

	@Override
	public boolean setUrlsForStream(StreamDefinition stream) {
		Iterator<AppStatusResource> statsIterator = operations.status().iterator();
		while (statsIterator.hasNext()) {
			AppStatusResource appStatus = statsIterator.next();
			for (Application application : stream.getApplications()) {
				if (appStatus.getDeploymentId().toLowerCase().contains(stream.getName().toLowerCase())
						&& appStatus.getDeploymentId().endsWith((application.getName()))) {
					if (!setUrlForApplication(application, appStatus)) {
						return false;
					}
					else {
						setInstanceUrlsForApplication(application, appStatus);
					}
				}
			}
		}
		return true;
	}

	@Override
	public void addDeploymentProperties(StreamDefinition stream, Map<String, String> properties) {
	}

	@Override
	public String getLogfileName() {
		return "test.log";
	}

	/**
	 * Set URL for application based on 'url' attribute from the instances. Can be
	 * overridden by platform implementation that do not provide the 'url' instance
	 * attribute.
	 *
	 * @param application
	 * @param appStatus
	 * @return whether url was set
	 */
	protected boolean setUrlForApplication(Application application, AppStatusResource appStatus) {
		Iterator<AppInstanceStatusResource> resourceIterator = appStatus.getInstances().iterator();
		while (resourceIterator.hasNext()) {
			AppInstanceStatusResource resource = resourceIterator.next();
			if (resource.getAttributes().containsKey(URL)) {
				application.setUrl(resource.getAttributes().get(URL));
				break;
			}
			else {
				return false;
			}
		}
		return true;
	}

	/**
	 * Set URL the instance URLs based on 'url' attribute from each instances. Can be
	 * overridden by platform implementations that do not provide the 'url' instance
	 * attribute.
	 *
	 * @param application
	 * @param appStatus
	 * @return whether any url was set
	 */
	protected void setInstanceUrlsForApplication(Application application, AppStatusResource appStatus) {
		Iterator<AppInstanceStatusResource> resourceIterator = appStatus.getInstances().iterator();
		while (resourceIterator.hasNext()) {
			AppInstanceStatusResource resource = resourceIterator.next();
			if (resource.getAttributes().containsKey(URL)) {
				application.addInstanceUrl(resource.getInstanceId(), resource.getAttributes().get(URL));
			}
		}
	}

}
