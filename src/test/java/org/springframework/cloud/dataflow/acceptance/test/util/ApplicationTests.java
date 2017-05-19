/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.acceptance.test.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the basic functionality of the Application util.
 *
 * @author Glenn Renfro
 */
public class ApplicationTests {

	public static final String DEFAULT_URI = "http://myURI";

	@Test
	public void testApplication() {
		Application application = new Application( "BAR");
		application.setUri(DEFAULT_URI);
		validateApplication(application, "BAR", 	DEFAULT_URI);
	}
	@Test
	public void testApplicationParam() {
		Application application = new Application("BAR --BAZ=FOO");
		application.setUri(DEFAULT_URI);
		validateApplication(application, "BAR --BAZ=FOO", DEFAULT_URI);
	}

	private void validateApplication(Application application, String definition, String uri) {
		assertEquals(definition, application.getDefinition());
		assertEquals(uri, application.getUri());
	}

}
