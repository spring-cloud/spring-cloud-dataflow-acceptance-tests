/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.cloud.dataflow.perf.test.task.configuration;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(TaskPerformanceProperties.class)
public class TaskPerformanceConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(TaskPerformanceConfiguration.class);
	@Autowired
	TaskPerformanceProperties properties;

	@Autowired
	private DataFlowOperations dataFlowOperations;

	@Autowired(required = false)
	private DataSource dataSource;

	@Bean
	public ApplicationRunner applicationRunner(Environment environment) {
		return args -> {
			if (properties.getCleanup()) {
				TaskUtils.cleanup(properties.getTaskPrefix(), dataFlowOperations);
			}
			else {
				TaskUtils.createTaskDefinitions(properties.getTaskPrefix(), properties.getTaskDefinitionCount(),
						dataFlowOperations);
				if (properties.getAddTaskExecutions()) {
					if (dataSource != null) {
                        logger.info("Datasource configured:" + environment.getProperty("spring.datasource.url"));
						TaskUtils.dbInsertTaskExecutions(properties.getTaskExecutionCount(), properties.getJobInstancesPerTaskExecution(),
								TaskUtils.getTaskDefinitionsByPrefix(properties.getTaskPrefix(), dataFlowOperations),
								dataSource);
					}
					else {
					    logger.warn("Datasource not configured. Will not create task executions.");
                    }
				}
			}
		};
	}
}
