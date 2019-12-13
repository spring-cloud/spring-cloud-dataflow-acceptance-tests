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

package org.springframework.cloud.dataflow.acceptence.tests.batch.remote.partition.configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.jsonwebtoken.lang.Assert;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.task.batch.partition.CommandLineArgsProvider;
import org.springframework.cloud.task.batch.partition.DeployerPartitionHandler;
import org.springframework.cloud.task.batch.partition.DeployerStepExecutionHandler;
import org.springframework.cloud.task.batch.partition.EnvironmentVariablesProvider;
import org.springframework.cloud.task.batch.partition.NoOpEnvironmentVariablesProvider;
import org.springframework.cloud.task.batch.partition.SimpleCommandLineArgsProvider;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * @author Michael Minella
 * @author David Turanski
 */
@Configuration
@EnableBatchProcessing
@EnableTask
public class BatchConfiguration {

	public static final int NUM_TASKS = 3;

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private ConfigurableApplicationContext context;

	@Autowired
	DelegatingResourceLoader resourceLoader;

	@Value("${artifact:}")
	private String artifact;

	@Bean
	@Profile("!worker")
	public DeployerPartitionHandler partitionHandler(
			TaskLauncher taskLauncher,
			JobExplorer jobExplorer,
			TaskRepository taskRepository,
			EnvironmentVariablesProvider environmentVariablesProvider,
			CommandLineArgsProvider commandLineArgsProvider) throws Exception {

		DeployerPartitionHandler partitionHandler = new DeployerPartitionHandler(taskLauncher, jobExplorer,
				resourceLoader.getResource(artifact), "step1", taskRepository);

		partitionHandler.setCommandLineArgsProvider(commandLineArgsProvider);
		partitionHandler.setMaxWorkers(3);
		partitionHandler.setEnvironmentVariablesProvider(environmentVariablesProvider);

		return partitionHandler;
	}

	@Bean
	@Profile("!worker")
	public EnvironmentVariablesProvider environmentVariablesProvider() {
		return new NoOpEnvironmentVariablesProvider();
	}

	@Bean
	@Profile("!worker")
	public SimpleCommandLineArgsProvider commandLineArgsProvider() {
		List<String> commandLineArgs = new ArrayList<>();
		commandLineArgs.add("--spring.profiles.active=worker");
		commandLineArgs.add("--spring.cloud.task.initialize.enable=false");
		commandLineArgs.add("--spring.batch.initializer.enabled=false");
		commandLineArgs.add("--spring.datasource.initialize=false");
		/*
		 * Avoid passing the current task execution id as it will result in a duplicate.
		 */
		SimpleCommandLineArgsProvider commandLineArgsProvider = new FilteringCommandLineArgsProvider(
				Collections.singletonList("spring.cloud.task.executionid"));
		commandLineArgsProvider.setAppendedArgs(commandLineArgs);
		return commandLineArgsProvider;
	}

	@Bean
	@StepScope
	public Partitioner partitioner() {

		return gridSize -> {
			Map<String, ExecutionContext> map = new HashMap<>(gridSize);
			for (int i = 0; i < NUM_TASKS; i++) {
				ExecutionContext context = new ExecutionContext();
				context.put("instance", i);
				map.put("instance" + i, context);
			}
			return map;
		};
	}

	@Bean
	@Profile("worker")
	public DeployerStepExecutionHandler stepExecutionHandler(JobExplorer jobExplorer) {
		return new DeployerStepExecutionHandler(this.context, jobExplorer, this.jobRepository);
	}

	@Bean
	@Profile("!worker")
	public Step partitionedMaster(PartitionHandler partitionHandler, Partitioner partitioner) {
		return this.stepBuilderFactory.get("step1")
				.partitioner(step1().getName(), partitioner)
				.step(step1())
				.partitionHandler(partitionHandler)
				.build();
	}

	@Bean
	public Step step1() {
		return this.stepBuilderFactory.get("step1").tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED)
				.build();
	}

	@Bean
	@Profile("!worker")
	public Job parallelStepsJob(Step partitionedMaster) {
		return this.jobBuilderFactory.get("parallelStepsJob")
				.start(partitionedMaster)
				.incrementer(jobParameters -> {
					long id = jobParameters.getLong("run.id", Long.valueOf(1)) + 1;
					return new JobParametersBuilder().addLong("run.id", id).toJobParameters();
				})
				.build();
	}

	static class FilteringCommandLineArgsProvider extends SimpleCommandLineArgsProvider {
		private final List<String> blackList;

		public FilteringCommandLineArgsProvider(List<String> blackList) {
			Assert.notNull(blackList, "'blackList' cannot be null");
			this.blackList = blackList;
		}

		@Override
		public List<String> getCommandLineArgs(ExecutionContext executionContext) {
			return super.getCommandLineArgs(executionContext)
					.stream()
					.filter(
							arg -> !blackList.contains(arg.substring(0, arg.indexOf("=")).replaceAll("^--", "")))
					.collect(Collectors.toList());
		}
	}
}
