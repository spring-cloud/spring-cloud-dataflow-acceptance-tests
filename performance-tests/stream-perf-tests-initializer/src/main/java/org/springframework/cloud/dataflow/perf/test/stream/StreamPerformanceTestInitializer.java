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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.integration.test.DataFlowOperationsITConfiguration;
import org.springframework.cloud.dataflow.integration.test.IntegrationTestProperties;
import org.springframework.cloud.dataflow.integration.test.util.RuntimeApplicationHelper;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.dsl.DeploymentPropertiesBuilder;
import org.springframework.cloud.dataflow.rest.client.dsl.Stream;
import org.springframework.cloud.dataflow.rest.client.dsl.StreamDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties(StreamPerformanceTestProperties.class)
@Import({ DataFlowOperationsITConfiguration.class })
public class StreamPerformanceTestInitializer {

    private static final Logger logger = LoggerFactory.getLogger(StreamPerformanceTestInitializer.class);

    private static final String SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME = "spring.cloud.dataflow.skipper.platformName";

    public static void main(String... args) {
        new SpringApplicationBuilder(StreamPerformanceTestInitializer.class).web(WebApplicationType.NONE).run(args);
    }

    /**
     * Workaround for:
     * <code>
     *     Parameter 0 of method runtimeApplicationHelper in
     *     org.springframework.cloud.dataflow.integration.test.DataFlowOperationsITConfiguration required a bean of
     *     type 'org.springframework.cloud.dataflow.rest.client.DataFlowTemplate' that could not be found.
     * </code>
     */
    @Bean
    public DataFlowTemplate dataFlowTemplate(DataFlowOperations dataFlowOperations) {
        return (DataFlowTemplate) dataFlowOperations;
    }

    @Bean
    public CommandLineRunner commandLineRunner(DataFlowOperations dataFlowOperations, RuntimeApplicationHelper runtimeApps,
        IntegrationTestProperties testProperties, StreamPerformanceTestProperties streamPerfTestProperties) {

        Awaitility.setDefaultPollInterval(Duration.ofSeconds(5L));
        Awaitility.setDefaultTimeout(Duration.ofMinutes(15L));

        return args -> {
            if (streamPerfTestProperties.getCleanup()) {
                logger.info("Destroy existing streams!");
                dataFlowOperations.streamOperations().list().getContent().stream()
                    .filter(streamDefinition -> streamDefinition.getName().startsWith(streamPerfTestProperties.getStreamPrefix()))
                    .forEach(streamDefinition -> dataFlowOperations.streamOperations().destroy(streamDefinition.getName()));
                //dataFlowOperations.streamOperations().destroyAll();
            }
            else {
                List<StreamDefinition> streamDefinitionList = new ArrayList<>();
                logger.info("Creating Stream definitions...");
                for (int i = 0; i < streamPerfTestProperties.getStreamDefinitionsNumber(); i++) {
                    StreamDefinition streamDef = Stream.builder(dataFlowOperations)
                        .name(randomStreamName(streamPerfTestProperties.getStreamPrefix()))
                        .definition(streamPerfTestProperties.getStreamDefinition())
                        .create();
                    streamDefinitionList.add(streamDef);
                }

                logger.info("Defined stream #: " + dataFlowOperations.streamOperations().list().getContent().size());

                if (streamPerfTestProperties.isBatchDeploymentEnabled()) {
                    int batchSize = streamPerfTestProperties.getBatchDeploymentSize();

                    int intervals = streamDefinitionList.size() / batchSize;

                    for (int batch = 0; batch < intervals; batch++) {
                        List<Stream> deployedStreams = new ArrayList<>();
                        for (int i = batch * batchSize; i < batch * batchSize + batchSize; i++) {
                            if (i < streamDefinitionList.size()) {
                                Stream stream = streamDefinitionList.get(i).deploy(testDeploymentProperties(runtimeApps));
                                deployedStreams.add(stream);
                                Awaitility.await().until(() -> stream.getStatus().equals("deployed"));
                            }
                        }

                        for (Stream stream : deployedStreams) {
                            stream.undeploy();
                            Awaitility.await()
                                .until(() -> stream.getStatus().equals("undeployed"));
                        }
                    }
                }
            }
        };
    }

    private static String randomStreamName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 10);
    }

    private Map<String, String> testDeploymentProperties(RuntimeApplicationHelper runtimeApps) {
        DeploymentPropertiesBuilder propertiesBuilder = new DeploymentPropertiesBuilder()
            .put(SPRING_CLOUD_DATAFLOW_SKIPPER_PLATFORM_NAME, runtimeApps.getPlatformName())
            .put("app.*.logging.file", "/tmp/${PID}-test.log") // Keep it for Boot 2.x compatibility.
            .put("app.*.logging.file.name", "/tmp/${PID}-test.log")
            .put("app.*.endpoints.logfile.sensitive", "false")
            .put("app.*.endpoints.logfile.enabled", "true")
            .put("app.*.management.endpoints.web.exposure.include", "*")
            .put("app.*.spring.cloud.streamapp.security.enabled", "false");

        if (runtimeApps.getPlatformType().equalsIgnoreCase(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            propertiesBuilder.put("app.*.server.port", "8080");
            propertiesBuilder.put("deployer.*.kubernetes.createLoadBalancer", "true"); // requires LoadBalancer support on the platform
        }

        return propertiesBuilder.build();
    }
}
