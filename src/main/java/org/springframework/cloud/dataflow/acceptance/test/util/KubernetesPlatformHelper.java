/*
 * Copyright 2016-2019 the original author or authors.
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

import java.util.Map;

import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.resource.AppInstanceStatusResource;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;

/**
 * Implementation of {@link PlatformHelper} for Kubernetes.
 *
 * @author Thomas Risberg
 * @author Chris Schaefer
 */
public class KubernetesPlatformHelper extends AbstractPlatformHelper {
    private String appHost;

    public KubernetesPlatformHelper(RuntimeOperations operations, String appHost) {
        super(operations);
        this.appHost = appHost;
    }

    @Override
    public void addDeploymentProperties(StreamDefinition stream, Map<String, String> properties) {
        properties.put("app.*.server.port", "80");

        // currently app starters have security enabled on endpoints..
        properties.put("app.*.spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration");
    }

    protected boolean setUrlForApplication(Application application, AppStatusResource appStatus) {
        application.setUrl("https://" + appStatus.getDeploymentId() + getApplicationHost());
        return true;
    }

    protected void setInstanceUrlsForApplication(Application application, AppStatusResource appStatus) {
        for (AppInstanceStatusResource resource : appStatus.getInstances()) {
            String hostName = appStatus.getDeploymentId();

            if (appStatus.getInstances().getContent().size() > 1) {
                hostName = resource.getInstanceId();
            }

            application.addInstanceUrl(resource.getInstanceId(), "https://" + hostName + getApplicationHost());
        }
    }

    protected String getApplicationHost() {
	    return appHost;
    }
}
