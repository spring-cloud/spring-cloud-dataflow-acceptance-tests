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

package org.springframework.cloud.dataflow.acceptance.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.ClassRule;
import org.junit.Test;

import org.springframework.cloud.dataflow.acceptance.test.util.SchedulerRule;
import org.springframework.cloud.dataflow.rest.resource.ScheduleInfoResource;
import org.springframework.hateoas.PagedResources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class SchedulerTests extends AbstractTaskTests {

	private final static String DEFAULT_SCDF_EXPRESSION_KEY = "scheduler.cron.expression";

	private final static String DEFAULT_CRON_EXPRESSION = "56 20 ? * *";

	@ClassRule
	public static SchedulerRule cfAvailable = new SchedulerRule();

	@Test
	public void listTest() {
		final int ENTRY_COUNT = 2;
		Set<String> scheduleSet = new HashSet(2);
		for(int i = 0; i < ENTRY_COUNT; i++) {
			scheduleSet.add(createDefaultSchedule());
		}
		PagedResources<ScheduleInfoResource> pagedResources = listSchedules();
		assertThat(pagedResources.getMetadata().getTotalElements()).isEqualTo(ENTRY_COUNT);
		Iterator<ScheduleInfoResource> iterator = pagedResources.getContent().iterator();
		for(int i = 0; i < ENTRY_COUNT; i++) {
			ScheduleInfoResource resource = iterator.next();
			if(scheduleSet.contains(resource.getScheduleName())) {
				assertThat(resource.getScheduleProperties().get(DEFAULT_CRON_EXPRESSION_KEY)).isEqualTo(DEFAULT_CRON_EXPRESSION);
			} else {
				fail(String.format("%s schedule is missing from result set of list."));
			}
		}
	}

	@Test
	public void scheduleTest() {
		assertThat(verifyScheduleExists(createDefaultSchedule())).isTrue();
	}

	@Test
	public void deleteTest() {
		String scheduleName = createDefaultSchedule();
		assertThat(verifyScheduleExists(scheduleName)).isTrue();
		unschedule(scheduleName);
		assertThat(verifyScheduleExists(scheduleName)).isFalse();
	}

	@Test
	public void filterByTaskDefNameTest() {
		final int ENTRY_COUNT = 2;
		String[] taskNames = new String[ENTRY_COUNT];
		String[] scheduleNames = new String[ENTRY_COUNT];
		for(int i = 0; i < ENTRY_COUNT; i++) {
			taskNames[i] = taskCreate("timestamp");
            scheduleNames[i] = createDefaultSchedule(taskNames[i]) + "-" + "scdf-" + taskNames[i];
		}
		for(int i = 0; i < ENTRY_COUNT; i++) {
			PagedResources<ScheduleInfoResource> pagedResources = listSchedules(taskNames[i]);
			assertThat(pagedResources.getMetadata().getSize()).isEqualTo(1);
			verifyScheduleIsValid(pagedResources.getContent().iterator().next(), scheduleNames[i], DEFAULT_CRON_EXPRESSION);
		}
	}

	@Test
	public void getScheduleTest() {
		String scheduleName = createDefaultSchedule();
		ScheduleInfoResource scheduleInfoResource = schedulerOperations.getSchedule(scheduleName);
		verifyScheduleIsValid(scheduleInfoResource, scheduleName, DEFAULT_CRON_EXPRESSION);
	}

	private String createDefaultSchedule() {
		String taskDefinitionName = taskCreate("timestamp");
		return  createDefaultSchedule(taskDefinitionName) + "-" +"scdf-" + taskDefinitionName ;
	}

	private String createDefaultSchedule(String taskName) {
		Map<String,String> taskProperties = new HashMap<>(1);
		taskProperties.put(DEFAULT_SCDF_EXPRESSION_KEY, DEFAULT_CRON_EXPRESSION);
		List<String> taskArgs = new ArrayList<>();
		return schedule(taskName, taskProperties, taskArgs);
	}

}
