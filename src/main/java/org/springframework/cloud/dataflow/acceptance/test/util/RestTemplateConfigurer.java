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

package org.springframework.cloud.dataflow.acceptance.test.util;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class RestTemplateConfigurer {

	private boolean skipSslValidation;

	public RestTemplateConfigurer skipSslValidation(boolean skipSslValidation) {
		this.skipSslValidation = skipSslValidation;
		return this;
	}

	public RestTemplate configure() {
		RestTemplate restTemplate = new RestTemplate();

		if (skipSslValidation) {

			HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
			try {
				httpClientBuilder.setSSLContext(SSLContexts.custom().loadTrustMaterial((chain, authType) -> true).build());
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}

			restTemplate.setRequestFactory(
					new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build()));

		}

		return restTemplate;
	}
}
