/*
 * Copyright 2018 the original author or authors.
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

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.SchedulerOperations;
import org.springframework.cloud.scheduler.spi.junit.AbstractExternalResourceTestSupport;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * Verifies that the Spring Cloud Data Flow Scheduler is available.
 *
 * @author Glenn Renfro
 * @author David Turanski
 */
public class SchedulerRule extends AbstractExternalResourceTestSupport<DataFlowTemplate> {

	private ConfigurableApplicationContext context;

	public SchedulerRule() {
		super("SCHEDULER");
	}

	@Override
	protected void cleanupResource() throws Exception {
		context.close();
	}

	@Override
	protected void obtainResource() throws Exception {
		context = new SpringApplicationBuilder(Config.class).web(false).run();
		resource = context.getBean(DataFlowTemplate.class);

		SchedulerOperations schedulerOperations = resource.schedulerOperations();
		Assert.notNull(schedulerOperations, "no SchedulerOperations bean is available.  Skipping test.");
	}

	@Configuration
	@EnableConfigurationProperties
	public static class Config {

		@Bean
		@ConfigurationProperties
		public TestConfigurationProperties cloudFoundryConnectionProperties() {
			return new TestConfigurationProperties();
		}

		@Bean
		public DataFlowTemplate dataFlowTemplate(TestConfigurationProperties configurationProperties) {
			return DataFlowTemplateConfigurer.create(configurationProperties.getServerUri())
				.configure();
		}

	}
}
