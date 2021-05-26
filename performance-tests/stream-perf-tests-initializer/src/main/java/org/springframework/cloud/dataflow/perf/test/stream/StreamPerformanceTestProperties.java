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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(prefix = "org.springframework.cloud.dataflow.stream.performance")
public class StreamPerformanceTestProperties {
    /**
     * Then number of definitions to create.
     */
    private int streamDefinitionsNumber = 100;

    /**
     * The stream definition used to define test streams.
     */
    private String streamDefinition = "http | transform --expression=payload.toUpperCase() | log";

    /**
     * The prefix to be used for stream definition names.
     */
    private String streamPrefix = "perf-test-stream";

    /**
     * Removes all stream definitions with a stream name starting with the prefix.
     */
    private Boolean cleanup = false;

    /**
     * Whether to test deploy/undeploy the defined streams. The deploy/undeploy are performed in batches.
     */
    private Boolean batchDeploymentEnabled = false;

    /**
     * If batchDeploymentEnabled is true the test will deploy and then undeploy all defined streams in batches
     * of size batchDeploymentSize.
     * For example if the batch size is 10, then it will deploy the first batch of 10 streams, wait until all 10 are deployed
     * and then undeploy them. Then move to the second batch of 10 and do the same.  ...
     */
    private int batchDeploymentSize = 10;

    public int getStreamDefinitionsNumber() {
        return streamDefinitionsNumber;
    }

    public void setStreamDefinitionsNumber(int streamDefinitionsNumber) {
        this.streamDefinitionsNumber = streamDefinitionsNumber;
    }

    public String getStreamDefinition() {
        return streamDefinition;
    }

    public void setStreamDefinition(String streamDefinition) {
        this.streamDefinition = streamDefinition;
    }

    public String getStreamPrefix() {
        return streamPrefix;
    }

    public void setStreamPrefix(String streamPrefix) {
        this.streamPrefix = streamPrefix;
    }

    public Boolean getCleanup() {
        return cleanup;
    }

    public void setCleanup(Boolean cleanup) {
        this.cleanup = cleanup;
    }

    public int getBatchDeploymentSize() {
        return batchDeploymentSize;
    }

    public void setBatchDeploymentSize(int batchDeploymentSize) {
        this.batchDeploymentSize = batchDeploymentSize;
    }

    public Boolean isBatchDeploymentEnabled() {
        return batchDeploymentEnabled;
    }

    public void setBatchDeploymentEnabled(Boolean batchDeploymentEnabled) {
        this.batchDeploymentEnabled = batchDeploymentEnabled;
    }
}
