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

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

import org.springframework.analytics.rest.domain.CounterResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.repository.redis.RedisMetricRepository;
import org.springframework.cloud.dataflow.acceptance.test.util.StreamDefinition;
import org.springframework.cloud.dataflow.rest.resource.about.AboutResource;
import org.springframework.cloud.stream.app.test.redis.RedisTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

/**
 * Executes acceptance test for the analytics functionality
 * @author Mark Pollack
 */
public class AnalyticsTests extends AbstractStreamTests {

	@Rule
	public RedisTestSupport redisAvailableRule = new RedisTestSupport();

	@Autowired
	private RedisMetricRepository redisMetricRepository;

	@Test
	public void analyticsTest() {
		AboutResource aboutResource = this.dataFlowOperations.aboutOperation().get();
		String implementation = aboutResource.getVersionInfo().getImplementation().getName();
		assumeThat("Skipping test", "spring-cloud-dataflow-server",
				Matchers.not(implementation));
		if (!configurationProperties.getPlatformType().equals(PlatformTypes.LOCAL.getValue())) {
			return;
		}
		redisMetricRepository.reset("counter.httpCounter");
		StreamDefinition streamDefinition = StreamDefinition.builder("httpCounter")
				.definition("http | counter --name=httpCounter")
				//TODO bind redis service for PCF
				//TODO what about k8s?
				.build();
		deployStream(streamDefinition);
		assertTrue("Source not started", waitForLogEntry(streamDefinition.getApplication("http"), "Started HttpSource"));
		assertTrue("Sink not started", waitForLogEntry(streamDefinition.getApplication("counter"), "Started CounterSink"));

		httpPostData(streamDefinition.getApplication("http"), "Now is the time for all good men to come to the aid of their country.");

		// just to ensure the http post got to the counter sink.
		deploymentPause();

		CounterResource counterResource = dataFlowOperations().counterOperations().retrieve("httpCounter");
		assertEquals("Expected counter value of 1", 1, counterResource.getValue());
	}
}
