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


import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.integration.test.DataFlowIT;
import org.springframework.cloud.dataflow.integration.test.IntegrationTestProperties;
import org.springframework.cloud.dataflow.rest.client.dsl.DeploymentPropertiesBuilder;
import org.springframework.cloud.dataflow.rest.client.dsl.Stream;

@SpringBootTest
@EnableConfigurationProperties({ IntegrationTestProperties.class })
class DataFlowAT extends DataFlowIT {

    private static final Logger logger = LoggerFactory.getLogger(DataFlowAT.class);

    // -----------------------------------------------------------------------
    //                     STREAM  CONFIG SERVER (PCF ONLY)
    // -----------------------------------------------------------------------
    @Test
    @EnabledIfSystemProperty(named = "PLATFORM_TYPE", matches = "cloudfoundry")
    @DisabledIfSystemProperty(named = "SKIP_CLOUD_CONFIG", matches = "true")
    public void streamWithConfigServer() {

        logger.info("stream-server-config-test");

        try (Stream stream = Stream.builder(dataFlowOperations)
            .name("TICKTOCK-config-server")
            .definition("time | log")
            .create()
            .deploy(new DeploymentPropertiesBuilder()
                .putAll(testDeploymentProperties())
                .put("app.log.spring.profiles.active", "test")
                .put("deployer.log.cloudfoundry.services", "cloud-config-server")
                .put("app.log.spring.cloud.config.name", "MY_CONFIG_TICKTOCK_LOG_NAME")
                .build())) {

            Awaitility.await(stream.getName() + " failed to deploy!")
                .until(() -> stream.getStatus().equals(DataFlowIT.DEPLOYED));

            Awaitility.await("Source not started").until(
                () -> stream.logs(app("time")).contains("Started TimeSource"));
            Awaitility.await("Sink not started").until(
                () -> stream.logs(app("log")).contains("Started LogSink"));
            Awaitility.await("No output found").until(
                () -> stream.logs(app("log")).contains("TICKTOCK CLOUD CONFIG - TIMESTAMP:"));
        }
    }
}
