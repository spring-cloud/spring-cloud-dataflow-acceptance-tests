/*
 * Copyright 2017 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.resource.AppInstanceStatusResource;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author Vinicius Carvalho
 */
public class PlatformHelperTests {

	private String STREAM_NAME = "test-stream";

	private StreamDefinition stream;

	@Mock
	private RuntimeOperations runtimeOperations;

	@Before
	public void setUp() {
		stream = StreamDefinition.builder(STREAM_NAME).definition("time | log").build();
		MockitoAnnotations.initMocks(this);
		List<AppStatusResource> appStatusResources = new ArrayList<>();
		AppStatusResource logStatus = new AppStatusResource(STREAM_NAME + "-log", "deployed");
		logStatus.setInstances(new Resources<>(Collections.singletonList(
				new AppInstanceStatusResource(STREAM_NAME + "-log", "deployed",
						Collections.singletonMap("url", "https://log")))));
		appStatusResources.add(logStatus);
		AppStatusResource timeStatus = new AppStatusResource(STREAM_NAME + "-time", "deployed");
		timeStatus.setInstances(new Resources<>(Collections.singletonList(
				new AppInstanceStatusResource(STREAM_NAME + "-time", "deployed",
						Collections.singletonMap("url", "https://time"))), (new Link[0])));
		appStatusResources.add(timeStatus);
		PagedResources<AppStatusResource> resources = new PagedResources<>(appStatusResources, null, new Link("test"));
		when(this.runtimeOperations.status()).thenReturn(resources);
	}

	@Test
	public void testDefaultPlatformHelper() {
		PlatformHelper platformHelper = new DefaultPlatformHelper(runtimeOperations);
		assertEquals("test.log", platformHelper.getLogfileName());
		assertTrue(platformHelper.setUrlsForStream(stream));
		assertEquals("https://log", stream.getApplication("log").getUrl());
		assertEquals("https://time", stream.getApplication("time").getUrl());
	}

	@Test
	public void testLocalPlatformHelper() {
		PlatformHelper platformHelper = new LocalPlatformHelper(runtimeOperations);
		assertTrue(platformHelper.getLogfileName().contains("test.log"));
		assertTrue(platformHelper.getLogfileName().contains("${PID}"));
	}

	@Test
	public void testKubernetesPlatformHelper() {
		PlatformHelper platformHelper = new KubernetesPlatformHelper(runtimeOperations);
		assertEquals("test.log", platformHelper.getLogfileName());
		assertTrue(platformHelper.setUrlsForStream(stream));
		assertEquals("https://log", stream.getApplication("log").getUrl());
		assertEquals("https://time", stream.getApplication("time").getUrl());
	}

}
