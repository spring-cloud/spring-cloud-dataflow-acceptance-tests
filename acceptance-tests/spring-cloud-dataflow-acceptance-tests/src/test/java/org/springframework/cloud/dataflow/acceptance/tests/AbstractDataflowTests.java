/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.cloud.dataflow.acceptance.tests;

import org.springframework.cloud.dataflow.acceptance.core.DockerComposeInfo;
import org.springframework.cloud.dataflow.acceptance.tests.support.AssertUtils;

import com.palantir.docker.compose.connection.DockerPort;

public abstract class AbstractDataflowTests {

	protected static void start(DockerComposeInfo dockerComposeInfo, String id) {
		dockerComposeInfo.id(id).start();
	}

	protected static void stop(DockerComposeInfo dockerComposeInfo, String id) {
		dockerComposeInfo.id(id).stop();
	}

	protected static void upgradeSkipper(DockerComposeInfo dockerComposeInfo, String from, String to, String container) {
		stop(dockerComposeInfo, from);
		start(dockerComposeInfo, to);
		assertSkipperServerRunning(dockerComposeInfo, to, container);
	}

	protected static void assertDataflowServerRunning(DockerComposeInfo dockerComposeInfo, String id, String container) {
		DockerPort port = dockerComposeInfo.id(id).getRule().containers().container(container).port(9393);
		String url = "http://" + port.getIp() + ":" + port.getExternalPort() + "/about";
		AssertUtils.assertDataflowServerRunning(url);
		AssertUtils.assertSkipperServerRunning(url);
	}

	protected static void assertSkipperServerRunning(DockerComposeInfo dockerComposeInfo, String id, String container) {
		DockerPort port = dockerComposeInfo.id(id).getRule().containers().container(container).port(7577);
		String url = "http://" + port.getIp() + ":" + port.getExternalPort() + "/api/about";
		AssertUtils.assertSkipperServerRunning(url);
	}
}
