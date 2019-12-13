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

package org.springframework.cloud.dataflow.acceptence.tests.batch.remote.partition;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.deployer.resource.docker.DockerResourceLoader;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResourceLoader;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.cloud.deployer.spi.cloudfoundry.CloudFoundryDeployerAutoConfiguration;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesAutoConfiguration;
import org.springframework.cloud.deployer.spi.local.LocalDeployerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;


/**
 * Disable default boot autoconfiguration since we depend on all the deployers to enable this app for any platform.
 * We explicitly enable  the deployer corresponding to the `platform` property value.
 *
 * @author David Turanski
 */
@SpringBootApplication(exclude = {
		CloudFoundryDeployerAutoConfiguration.class,
		LocalDeployerAutoConfiguration.class,
		KubernetesAutoConfiguration.class
})
public class BatchRemotePartitionApplication {

	public static void main(String[] args) {

	    new SpringApplicationBuilder(BatchRemotePartitionApplication.class)
            .web(WebApplicationType.NONE)
            .run(args);
	}

    @Configuration
    @ConditionalOnProperty(value = "platform", havingValue = "cloudfoundry")
    @Import(CloudFoundryDeployerAutoConfiguration.class)
    static class CFDeployerConfiguration {
    }

    @Configuration
    @ConditionalOnProperty(value = "platform", havingValue = "kubernetes")
    @Import(KubernetesAutoConfiguration.class)
    static class K8sDeployerConfiguration {
    }

    @Configuration
    @ConditionalOnProperty(value = "platform", havingValue = "local", matchIfMissing = true)
    @Import(LocalDeployerAutoConfiguration.class)
    static class LocalDeployerConfiguration {
    }

    // This config is needed to resolve `docker://` URL protocol.
    // A maven aware resource loader is configured by default.
    @Bean
    public DelegatingResourceLoader delegatingResourceLoader(MavenProperties mavenProperties) {
        DockerResourceLoader dockerLoader = new DockerResourceLoader();
        MavenResourceLoader mavenResourceLoader = new MavenResourceLoader(mavenProperties);
        Map<String, ResourceLoader> loaders = new HashMap<>();
        loaders.put("docker", dockerLoader);
        loaders.put("maven", mavenResourceLoader);
        return new DelegatingResourceLoader(loaders);
    }
}
