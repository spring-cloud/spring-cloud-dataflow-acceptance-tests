/*
 * Copyright 2016-2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.dataflow.acceptance.test.util;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * @author Glenn Renfro
 * @author Thomas Risberg
 */
public class PlatformHelperTests {

	private String STREAM_NAME = "teststream";

	@Mock
	private RuntimeOperations runtimeOperations;

	private PlatformHelper platformHelper;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		List<AppStatusResource> list = new ArrayList<>();
		list.add(new AppStatusResource(STREAM_NAME,"TEST"));
		PagedResources<AppStatusResource> resources =
				new PagedResources<>(list, null,
						new Link("test"));
		when(this.runtimeOperations.status()).thenReturn(resources);
	}

	@Test
	public void testLocalUriHelperWithStream() {
		Stream stream = new Stream(STREAM_NAME);
		stream.setSource("time");
		stream.setSink("log");
		platformHelper = new LocalPlatformHelper(runtimeOperations);
		platformHelper.setUrisForStream(stream);
		assertEquals("time", stream.getSource().getDefinition());
		assertEquals("log", stream.getSink().getDefinition());
		assertEquals("${PID}", platformHelper.getLogfileName());
	}

	@Test
	public void testCloudFoundryUriHelperWithStream() {
		Stream stream = new Stream("teststream");
		stream.setSource("time");
		stream.setSink("log");
		platformHelper = new CloudFoundryPlatformHelper(runtimeOperations, "TESTSUFFIX");
		platformHelper.setUrisForStream(stream);
		assertEquals("time", stream.getSource().getDefinition());
		assertEquals("log", stream.getSink().getDefinition());
		assertEquals("test.log", platformHelper.getLogfileName());
	}
}
