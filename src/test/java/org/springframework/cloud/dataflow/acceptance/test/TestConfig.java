/*
 * Copyright 2020 the original author or authors.
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

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.deployer.spi.cloudfoundry.CloudFoundryDeployerAutoConfiguration;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesAutoConfiguration;
import org.springframework.cloud.deployer.spi.local.LocalDeployerAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;


@SpringBootApplication(exclude = {
    CloudFoundryDeployerAutoConfiguration.class,
    KubernetesAutoConfiguration.class,
    LocalDeployerAutoConfiguration.class
})
public class TestConfig {
    @ConditionalOnProperty(value = "PLATFORM_TYPE", havingValue = "local", matchIfMissing = true)
    @TestPropertySource("classpath:application-local.yml")
    @Configuration
    // @Import(LocalDeployerAutoConfiguration.class)
    static class TestConfigLocal {

    }

    @ConditionalOnProperty(value = "PLATFORM_TYPE", havingValue = "kubernetes")
    @TestPropertySource("classpath:application-k8s.yml")
    @Configuration
    // @Import(KubernetesAutoConfiguration.class)
    static class TestConfigKubernetes {

    }

    @ConditionalOnProperty(value = "PLATFORM_TYPE", havingValue = "cloudfoundry")
    @Configuration
    // @Import(CloudFoundryDeployerAutoConfiguration.class)
    static class TestConfigCloudFoundry {

    }
}
