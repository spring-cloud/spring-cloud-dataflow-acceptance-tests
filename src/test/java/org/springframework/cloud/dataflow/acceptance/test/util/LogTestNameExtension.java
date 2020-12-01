/*
 * Copyright 2017-2020 the original author or authors.
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


import java.util.Optional;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Glenn Renfro
 */
public class LogTestNameExtension implements TestWatcher, BeforeEachCallback, AfterEachCallback {

	private final static Logger log = LoggerFactory.getLogger("junit.logTestName");

	public void testDisabled(ExtensionContext context, Optional<String> reason) {
		log.info("Test {} Disabled", context.getDisplayName());
	}

	public void testSuccessful(ExtensionContext context) {
		log.info("Test {} Succeeded", context.getDisplayName());
	}

	public void testAborted(ExtensionContext context, Throwable e) {
		log.error("!!! Test {} Aborted !!!", context.getDisplayName(), e);
	}

	public void testFailed(ExtensionContext context, Throwable e) {
		log.error("!!! Test {} Failed !!!", context.getDisplayName(), e);
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		log.info("Starting Test {}", context.getDisplayName());
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		log.info("Finished Test {}", context.getDisplayName());
	}
}
