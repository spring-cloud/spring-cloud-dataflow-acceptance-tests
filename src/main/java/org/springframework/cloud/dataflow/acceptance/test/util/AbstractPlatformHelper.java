/*
 * Copyright 2016-2020 the original author or authors.
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

import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.resource.AppInstanceStatusResource;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;
/**
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author Vinicius Carvalho
 * @author David Turanski
 */
public abstract class AbstractPlatformHelper implements PlatformHelper {

	protected Logger logger = LoggerFactory.getLogger(this.getClass());

	protected final String URL = "url";

	protected RuntimeOperations operations;

	public AbstractPlatformHelper(RuntimeOperations operations) {
		this.operations = operations;
	}

	@Override
	public boolean setUrlsForStream(StreamDefinition stream) {
		Iterator<AppStatusResource> statsIterator = operations.status().iterator();
		int numberOfAppsInStream = stream.getApplications().size();
		int numberOfAppsWithUrls = 0;
		while (statsIterator.hasNext()) {
			AppStatusResource appStatus = statsIterator.next();

			for (Application application : stream.getApplications()) {
				if (appStatus.getDeploymentId().toLowerCase().contains(stream.getName().toLowerCase())
						&& extractName(appStatus.getDeploymentId()).endsWith(application.getName())) {
					if (!setUrlForApplication(application, appStatus)) {
						return false;
					}
					else {
						if (setInstanceUrlsForApplication(application, appStatus)) {
							numberOfAppsWithUrls++;
						}
					}
				}
			}
		}
		if (numberOfAppsWithUrls == 0) {
			throw new IllegalStateException("Unable to get available urls for stream " + stream.getName() + "= " + stream.getDefinition());
		}
		if (numberOfAppsInStream == numberOfAppsWithUrls) {
			return true;
		}
		return false;
	}

	/**
	 * removes the '-v1' like suffix from the deploymentId like 'HTTP-TEST.log-v1'
	 */
	private static String extractName(String appName) {
		if (!appName.contains("-v")) {
			return appName;
		}
		return appName.split("-v")[0];
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
	 */
	protected boolean setInstanceUrlsForApplication(Application application, AppStatusResource appStatus) {
		boolean instanceUrlsAdded = false;
		Iterator<AppInstanceStatusResource> resourceIterator = appStatus.getInstances().iterator();
		if (!resourceIterator.hasNext()) {
			logger.error(appStatus.getDeploymentId() + " appStatus contains no instances for deployed application.");
		}

		while (resourceIterator.hasNext()) {
			AppInstanceStatusResource resource = resourceIterator.next();
			if (resource.getAttributes().containsKey(URL)) {
				instanceUrlsAdded = true;
				application.addInstanceUrl(resource.getInstanceId(), resource.getAttributes().get(URL));
			}
		}
		return instanceUrlsAdded;
	}

}
