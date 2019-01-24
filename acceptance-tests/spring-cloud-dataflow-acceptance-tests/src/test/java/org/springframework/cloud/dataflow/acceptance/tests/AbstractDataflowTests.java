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

import java.util.List;

import org.springframework.cloud.dataflow.acceptance.core.DockerComposeInfo;
import org.springframework.cloud.dataflow.acceptance.tests.support.AssertUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;
import com.palantir.docker.compose.connection.DockerPort;

public abstract class AbstractDataflowTests {

	private final static String STREAM_APPS_URI = "http://repo.spring.io/libs-release-local/org/springframework/cloud/stream/app/spring-cloud-stream-app-descriptor/Celsius.SR3/spring-cloud-stream-app-descriptor-Celsius.SR3.stream-apps-rabbit-maven";
	private final static String TASK_APPS_URI = "http://repo.spring.io/libs-release/org/springframework/cloud/task/app/spring-cloud-task-app-descriptor/Clark.SR1/spring-cloud-task-app-descriptor-Clark.SR1.task-apps-maven";

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

	protected static void upgradeDataflow(DockerComposeInfo dockerComposeInfo, String from, String to, String container) {
		stop(dockerComposeInfo, from);
		start(dockerComposeInfo, to);
		assertDataflowServerRunning(dockerComposeInfo, to, container, false);
	}

	protected static void assertDataflowServerRunning(DockerComposeInfo dockerComposeInfo, String id, String container) {
		assertDataflowServerRunning(dockerComposeInfo, id, container, true);
	}

	protected static void assertDataflowServerRunning(DockerComposeInfo dockerComposeInfo, String id, String container, boolean skipper) {
		DockerPort port = dockerComposeInfo.id(id).getRule().containers().container(container).port(9393);
		String url = "http://" + port.getIp() + ":" + port.getExternalPort() + "/about";
		AssertUtils.assertDataflowServerRunning(url);
		if (skipper) {
			AssertUtils.assertSkipperServerRunning(url);
		}
	}

	protected static void assertSkipperServerRunning(DockerComposeInfo dockerComposeInfo, String id, String container) {
		DockerPort port = dockerComposeInfo.id(id).getRule().containers().container(container).port(7577);
		String url = "http://" + port.getIp() + ":" + port.getExternalPort() + "/api/about";
		AssertUtils.assertSkipperServerRunning(url);
	}

	protected static List<String> registerApps(DockerComposeInfo dockerComposeInfo, String id, String container) {
		DockerPort port = dockerComposeInfo.id(id).getRule().containers().container(container).port(9393);
		String url = "http://" + port.getIp() + ":" + port.getExternalPort() + "/apps";
		RestTemplate template = new RestTemplate();

		MultiValueMap<String, Object> values = new LinkedMultiValueMap<>();
		values.add("uri", STREAM_APPS_URI);
		template.postForLocation(url, values);
		values = new LinkedMultiValueMap<>();
		values.add("uri", TASK_APPS_URI);
		template.postForLocation(url, values);

		return registeredApps(dockerComposeInfo, id, container);
	}

	protected static List<String> registeredApps(DockerComposeInfo dockerComposeInfo, String id, String container) {
		DockerPort port = dockerComposeInfo.id(id).getRule().containers().container(container).port(9393);
		String url = "http://" + port.getIp() + ":" + port.getExternalPort() + "/apps";
		RestTemplate template = new RestTemplate();

		String json = template.getForObject(url + "?size=2000", String.class);
		List<String> appsUris = JsonPath.read(json, "$._embedded.appRegistrationResourceList[*].uri");
		return appsUris;
	}
}
