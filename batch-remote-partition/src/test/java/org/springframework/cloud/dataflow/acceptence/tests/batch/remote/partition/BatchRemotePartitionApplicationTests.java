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

package org.springframework.cloud.dataflow.acceptence.tests.batch.remote.partition;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mariadb.jdbc.Driver;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("This test has a circular dependency on the built artifact for this app.")
class BatchRemotePartitionApplicationTests {

	@RegisterExtension
	MysqlLocalRunning mysqlLocalRunning = new MysqlLocalRunning();

	@Test
	void partitionedJobRuns() throws InterruptedException, ExecutionException, TimeoutException {
		ExecutorService executor = Executors.newSingleThreadExecutor();

		Future<?> result = executor.submit(() -> {
			new SpringApplicationBuilder(BatchRemotePartitionTestConfiguration.class).web(WebApplicationType.NONE)
					.run();
		});
		result.get(60, TimeUnit.SECONDS);
	}

	@Configuration
	@Import({ BatchRemotePartitionApplication.class })
	static class BatchRemotePartitionTestConfiguration {

		@Bean
		public JobExecutionListener jobValidator() {

			return new JobExecutionListener() {
				@Override
				public void beforeJob(JobExecution jobExecution) {
				}

				@Override
				public void afterJob(JobExecution jobExecution) {
					assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
					assertThat(jobExecution.getStepExecutions().size()).isEqualTo(4);
					assertThat(jobExecution.getStepExecutions().stream().map(se -> se.getStepName()))
							.containsExactlyInAnyOrder("step1", "step1:instance0", "step1:instance1",
									"step1:instance2");
				}
			};
		}

		@Bean
		BeanPostProcessor postProcessor(JobExecutionListener jobValidator) {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
					if (bean instanceof SimpleJob) {
						((SimpleJob) bean).registerJobExecutionListener(jobValidator);
					}
					return bean;
				}
			};
		}
	}

	static class MysqlLocalRunning implements ExecutionCondition {

		@Override
		public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
			ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
					.web(WebApplicationType.NONE).run("--spring.profiles.active=test");
			DataSource dataSource = context.getBean(DataSource.class);
			try {
				dataSource.getConnection();
			}
			catch (SQLException e) {
				e.printStackTrace();
				return ConditionEvaluationResult.disabled(
						"This test requires a persistent JDBC data source, cannot connect to the configured data source: "
								+ context.getEnvironment().getProperty("spring.datasource.url"));
			}
			return ConditionEvaluationResult.enabled(
					"connected to JDBC data source: " + context.getEnvironment().getProperty("spring.datasource.url"));

		}
	}

	@Configuration
	@Profile("test")
	static class TestConfiguration {
		@Bean
		DataSource testDataSource(@Value("spring.datasource.url") String url,
				@Value("spring.datasource.username") String username,
				@Value("spring.datasource.password") String password) {
			return new SimpleDriverDataSource(new Driver(), url, username, password);
		}
	}
}
