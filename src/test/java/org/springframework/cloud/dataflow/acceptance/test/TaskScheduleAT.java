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

package org.springframework.cloud.dataflow.acceptance.test;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.integration.test.DataFlowITProperties;
import org.springframework.cloud.dataflow.integration.test.TaskScheduleIT;
import org.springframework.cloud.dataflow.integration.test.util.DockerComposeFactoryProperties;

@SpringBootTest
@EnableConfigurationProperties({ DataFlowITProperties.class })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = DockerComposeFactoryProperties.TEST_DOCKER_COMPOSE_DISABLE_EXTENSION, matches = "true")
public class TaskScheduleAT extends TaskScheduleIT {
}
