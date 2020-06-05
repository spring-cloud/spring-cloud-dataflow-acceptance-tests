/*
 * Copyright 2020 the original author or authors.
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

package org.springframework.cloud.dataflow.acceptance.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.acceptance.test.util.TestConfigurationProperties;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.cloud.deployer.spi.cloudfoundry.CloudFoundryConnectionProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.Assert.assertTrue;

/**
 * Executes acceptance tests for the batch remote partition task.
 *
 * @author David Turanski
 */
@ContextConfiguration(classes = BatchRemotePartitionTests.ConditionalCloudFoundryTestConfiguration.class)
public class BatchRemotePartitionTests extends AbstractTaskTests {

	@Autowired
	private TestConfigurationProperties testConfigurationProperties;

	@Autowired(required = false)
	private CFConnectionProperties cfConnectionProperties;

	private static final String TASKNAME = "batch-remote-partition";

	@Override
	protected void registerTasks() {
		appRegistryOperations.register(TASKNAME, ApplicationType.task, artifactUriForPlatformType(), null, true);
		logger.info("Registered " + TASKNAME + " as " + artifactUriForPlatformType());
	}

	@Test
	public void runBatchRemotePartitionJob() {
		String taskDefinitionName = taskLaunch(
				getTaskDefinitionForPlatformType(),
				getDeploymentPropertiesForPlatformType(),
				getArgumentsForPlatformType());

		assertTrue(waitForTaskToComplete(taskDefinitionName, 4));
	}

	@Override
	protected Predicate<TaskExecutionResource> taskExecutionResourceTaskNameMatcher(String taskName) {
		return r -> r.getTaskName().startsWith(taskName);
	}

	private String getTaskDefinitionForPlatformType() {
		Map<String, String> properties = new HashMap<>();
		if (platformForPlatformType().equals("cloudfoundry")) {
			properties = new HashMap<>();
			properties.put(CFConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".username",
					cfConnectionProperties.getUsername());
			properties.put(CFConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".password",
					cfConnectionProperties.getPassword());
			properties.put(CFConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".org", cfConnectionProperties.getOrg());
			properties.put(CFConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".space",
					cfConnectionProperties.getSpace());
			properties.put(CFConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".url",
					cfConnectionProperties.getUrl().toString());
			properties.put(CFConnectionProperties.CLOUDFOUNDRY_PROPERTIES + ".skipSslValidation",
					String.valueOf(cfConnectionProperties.isSkipSslValidation()));
		}

		String taskDefinition = TASKNAME;
		for (Map.Entry<String, String> prop : properties.entrySet()) {
			taskDefinition += (String.format(" --%s=%s", prop.getKey(), prop.getValue()));
		}
		return taskDefinition;
	}

	private Map<String, String> getDeploymentPropertiesForPlatformType() {
		Map<String, String> deploymentProperties = new HashMap<>();
		String genericPlatform = platformForPlatformType();
		if (genericPlatform.equals("kubernetes")) {
			deploymentProperties.put("deployer.*.kubernetes.deployment-service-account-name",
					testConfigurationProperties.getDataflowServiceAccountName());
		}
		return deploymentProperties;
	}

	private List<String> getArgumentsForPlatformType() {
		List<String> args = new ArrayList<>();
		String genericPlatform = platformForPlatformType();
		args.add("--platform=" + genericPlatform);
		if (genericPlatform.equals("kubernetes")) {
			args.add("--artifact=" + artifactUriForPlatformType());
		}
		return args;
	}

	private String artifactUriForPlatformType() {
		return "kubernetes".equals(platformForPlatformType())
				? "docker://springcloud/batch-remote-partition:0.0.2-SNAPSHOT"
				: "maven://org.springframework.cloud.dataflow.acceptence.tests:batch-remote-partition:0.0.1-SNAPSHOT";
	}

	private String platformForPlatformType() {
		switch (testConfigurationProperties.getPlatformType()) {
		case "gke":
		case "pks":
			return "kubernetes";
		default:
			return testConfigurationProperties.getPlatformType();
		}
	}

	@ConfigurationProperties(CloudFoundryConnectionProperties.CLOUDFOUNDRY_PROPERTIES)
	static class CFConnectionProperties extends CloudFoundryConnectionProperties {
	}

	@Configuration
	@ConditionalOnProperty(value = "PLATFORM_TYPE" , havingValue = "cloudfoundry")
	@EnableConfigurationProperties({ BatchRemotePartitionTests.CFConnectionProperties.class })
	static class ConditionalCloudFoundryTestConfiguration {
	}
}
