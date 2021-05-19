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

package org.springframework.cloud.dataflow.perf.test.stream;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.integration.test.DataFlowOperationsITConfiguration;
import org.springframework.cloud.dataflow.integration.test.IntegrationTestProperties;
import org.springframework.cloud.dataflow.integration.test.util.RuntimeApplicationHelper;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.dsl.DeploymentPropertiesBuilder;
import org.springframework.cloud.dataflow.rest.client.dsl.Stream;
import org.springframework.cloud.dataflow.rest.client.dsl.StreamApplication;
import org.springframework.cloud.dataflow.rest.client.dsl.StreamDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties(StreamPerformanceTestInitializer.StreamPerfTestProperties.class)
@Import({ DataFlowOperationsITConfiguration.class })
public class StreamPerformanceTestInitializer {

    private static final Logger logger = LoggerFactory.getLogger(StreamPerformanceTestInitializer.class);

    private static final String SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME = "spring.cloud.dataflow.skipper.platformName";

    @Autowired
    private RuntimeApplicationHelper runtimeApps;

    public static void main(String... args) {
        new SpringApplicationBuilder(StreamPerformanceTestInitializer.class).web(WebApplicationType.NONE).run(args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(DataFlowOperations dataFlowOperations,
        RuntimeApplicationHelper runtimeApps,
        IntegrationTestProperties testProperties,
        StreamPerfTestProperties streamPerfTestProperties) {

        return args -> {

            if (streamPerfTestProperties.isCleanStreamsOnStart()) {
                logger.info("Destroy existing streams!");
                dataFlowOperations.streamOperations().destroyAll();
            }

            Map<String, StreamDefinition> streamDefinitionMap = new ConcurrentHashMap<>();

            logger.info("Creating Stream definitions...");
            for (int i = 0; i < streamPerfTestProperties.getNumberOfStreams(); i++) {
                StreamDefinition streamDef = Stream.builder(dataFlowOperations)
                    .name(randomStreamName())
                    .definition(streamPerfTestProperties.getStreamDefinition())
                    .create();
            }

            logger.info("Defined stream #: " + dataFlowOperations.streamOperations().list().getContent().size());


            //try (Stream stream = Stream.builder(dataFlowOperations)
            //    .name("transform-test")
            //    .definition("http | transform --expression=payload.toUpperCase() | log")
            //    .create()
            //    .deploy(testDeploymentProperties())) {
            //
            //    assertThat(stream.getStatus()).is(
            //        condition(status -> status.equals(DEPLOYING) || status.equals(PARTIAL)));
            //
            //    Awaitility.await().until(() -> stream.getStatus().equals(DEPLOYED));
            //
            //    String message = "Unique Test message: " + new Random().nextInt();
            //
            //    runtimeApps.httpPost(stream.getName(), "http", message);
            //
            //    Awaitility.await().until(() -> stream.logs(app("log")).contains(message.toUpperCase()));
            //}

            logger.info("Creating Mock Execution Data");
        };
    }

    private static String randomStreamName() {
        return "perf-stream-" + UUID.randomUUID().toString().substring(0, 10);
    }

    /**
     * For the purpose of testing, disable security, expose the all actuators, and configure logfiles.
     * @return Deployment properties required for the deployment of all test pipelines.
     */
    protected Map<String, String> testDeploymentProperties() {
        DeploymentPropertiesBuilder propertiesBuilder = new DeploymentPropertiesBuilder()
            .put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
            .put("app.*.logging.file", "/tmp/${PID}-test.log") // Keep it for Boot 2.x compatibility.
            .put("app.*.logging.file.name", "/tmp/${PID}-test.log")
            .put("app.*.endpoints.logfile.sensitive", "false")
            .put("app.*.endpoints.logfile.enabled", "true")
            .put("app.*.management.endpoints.web.exposure.include", "*")
            .put("app.*.spring.cloud.streamapp.security.enabled", "false");

        if (this.runtimeApps.getPlatformType().equalsIgnoreCase(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            propertiesBuilder.put("app.*.server.port", "8080");
            propertiesBuilder.put("deployer.*.kubernetes.createLoadBalancer", "true"); // requires LoadBalancer support on the platform
        }

        return propertiesBuilder.build();
    }

    protected StreamApplication app(String appName) {
        return new StreamApplication(appName);
    }

    @ConfigurationProperties("dataflow.stream.perf.tests")
    public static class StreamPerfTestProperties {
        private boolean cleanStreamsOnStart = true;

        private int numberOfStreams = 100;
        private String streamDefinition = "http | transform --expression=payload.toUpperCase() | log";

        public boolean isCleanStreamsOnStart() {
            return cleanStreamsOnStart;
        }

        public void setCleanStreamsOnStart(boolean cleanStreamsOnStart) {
            this.cleanStreamsOnStart = cleanStreamsOnStart;
        }

        public int getNumberOfStreams() {
            return numberOfStreams;
        }

        public void setNumberOfStreams(int numberOfStreams) {
            this.numberOfStreams = numberOfStreams;
        }

        public String getStreamDefinition() {
            return streamDefinition;
        }

        public void setStreamDefinition(String streamDefinition) {
            this.streamDefinition = streamDefinition;
        }
    }
}
