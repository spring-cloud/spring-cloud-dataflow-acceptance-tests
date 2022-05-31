/*
 * Copyright 2022 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.integration.test.IntegrationTestProperties;
import org.springframework.cloud.dataflow.integration.test.util.RuntimeApplicationHelper;
import org.springframework.cloud.dataflow.rest.client.AppRegistryOperations;
import org.springframework.cloud.dataflow.rest.client.DataFlowClientException;
import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.config.DataFlowClientProperties;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Corneil du Plessis
 */
@EnableConfigurationProperties(IntegrationTestProperties.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(DataFlowOperationsATConfiguration.class)
public class CommonTestBase {
    public static final String CURRENT_VERSION_NUMBER = "2.0.1";
    protected static final Logger logger = LoggerFactory.getLogger(DataFlowAT.class);
    /**
     * Test properties
     */
    @Autowired
    protected IntegrationTestProperties testProperties;
    /**
     * REST and DSL clients used to interact with the SCDF server and run the tests.
     */
    @Autowired
    protected DataFlowTemplate dataFlowOperations;

    /**
     * Runtime application helper.
     */
    @Autowired
    protected RuntimeApplicationHelper runtimeApps;
    @Autowired
    protected DataFlowClientProperties dataFlowClientProperties;

    protected void registerApp(String name, String url) {
        try {
            dataFlowOperations.appRegistryOperations().register(name, ApplicationType.app, url, null, true);
            logger.info("registerApp:{}:{}", name, url);
        } catch (DataFlowClientException x) {
            if (!x.toString().contains("exists")) {
                fail(x);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("registerApp:" + x);
            }
        }
    }

    protected void registerTask(String name, String artefact, String version) {
        AppRegistryOperations appRegistryOperations = this.dataFlowOperations.appRegistryOperations();
        try {
            String uri = artefact + ":" + version;
            appRegistryOperations.register(name, ApplicationType.task, uri, null, false);
            logger.info("registerTask:{}:{}", name, uri);
        } catch (DataFlowClientException x) {
            if (!x.toString().contains("already registered")) {
                logger.error("registerTask:" + name + ":Exception:" + x);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("registerTask:{}:{}", name, x.toString());
                }
            }
        }
    }

    protected void registerTimestampTasks() {
        if (this.runtimeApps.getPlatformType().equals(RuntimeApplicationHelper.KUBERNETES_PLATFORM_TYPE)) {
            registerTask("testtimestamp", "docker:springcloudtask/timestamp-task", CURRENT_VERSION_NUMBER);
            registerTask("testtimestamp-batch", "docker:springcloudtask/timestamp-batch-task", CURRENT_VERSION_NUMBER);
        } else {
            registerTask("testtimestamp", "maven://io.spring:timestamp-task", CURRENT_VERSION_NUMBER);
            registerTask("testtimestamp-batch", "maven://io.spring:timestamp-batch-task", CURRENT_VERSION_NUMBER);
        }
    }
}
