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

import java.util.Map;

import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;
import org.springframework.util.StringUtils;

/**
 * @author Glenn Renfro
 * @author Thomas Risberg
 */
public class CloudFoundryPlatformHelper extends AbstractPlatformHelper {

	private String cfSuffix;

	public CloudFoundryPlatformHelper(RuntimeOperations operations, String cfSuffix) {
		super(operations);
		this.cfSuffix = cfSuffix;
	}

	@Override
	protected boolean setUrlForApplication(Application application, AppStatusResource appStatus) {
		//TODO: remove this implementation once CF returns 'url' attribute
		if (StringUtils.hasText(appStatus.getDeploymentId()) && cfSuffix != null) {
			application.setUrl(String.format("http://%s.%s", appStatus.getDeploymentId(), cfSuffix));
			return true;
		}
		else {
			return false;
		}
	}
}
