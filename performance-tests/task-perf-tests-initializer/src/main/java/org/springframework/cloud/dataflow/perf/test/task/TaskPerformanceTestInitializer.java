/*
 * Copyright 2021 the original author or authors.
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

package org.springframework.cloud.dataflow.perf.test.task;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Creates some SCDF Task Definitions and populates the Task Execution tables with test
 * data.
 * @author David Turanski
 * @auther Glenn Renfro
 */
@SpringBootApplication
public class TaskPerformanceTestInitializer {

	public static void main(String... args) {
		new SpringApplicationBuilder(TaskPerformanceTestInitializer.class).web(WebApplicationType.NONE).run(args);
	}

}
