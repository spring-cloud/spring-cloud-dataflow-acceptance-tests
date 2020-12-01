/*
 * Copyright 2018-2020 the original author or authors.
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

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.SchedulerOperations;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Verifies that the Spring Cloud Data Flow Scheduler is available.
 *
 * @author Glenn Renfro
 * @author David Turanski
 */
public class SchedulerExtension implements ExecutionCondition, AfterTestExecutionCallback {

	private ConfigurableApplicationContext context;

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
		ConditionEvaluationResult conditionEvaluationResult = ConditionEvaluationResult.enabled("Scheduling Testing is Enabled");
		ConfigurableApplicationContext context = new SpringApplicationBuilder(Config.class).web(WebApplicationType.NONE).run();
		DataFlowTemplate resource = context.getBean(DataFlowTemplate.class);

		SchedulerOperations schedulerOperations = resource.schedulerOperations();
		if(schedulerOperations == null) {
			conditionEvaluationResult = ConditionEvaluationResult.disabled("no SchedulerOperations bean is available.  Skipping test.");
		}
		return conditionEvaluationResult;
	}

	@Override
	public void afterTestExecution(ExtensionContext extensionContext) throws Exception {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Configuration
	@EnableConfigurationProperties
	public static class Config {

		@Bean
		@ConfigurationProperties
		@Primary
		public TestConfigurationProperties cloudFoundryTestConfigurationProperties() {
			return new TestConfigurationProperties();
		}

		@Bean
		public DataFlowTemplate dataFlowTemplate(TestConfigurationProperties configurationProperties) {
			return DataFlowTemplateBuilder.serverUri(configurationProperties.getServerUri())
					.build();
		}

	}
}
