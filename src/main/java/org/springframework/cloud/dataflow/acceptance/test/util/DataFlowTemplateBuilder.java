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


import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

/**
 * @author David Turanski
 */
public class DataFlowTemplateBuilder {
	private boolean skipSslValidation = true;
	private final URI serverUri;
	private RestTemplate restTemplate;

	public static DataFlowTemplateBuilder serverUri(String serverUri) {
		Assert.hasText(serverUri, "'serverUri' must contain text");
		URI uri;
		try {
			uri = new URI(serverUri);
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e.getMessage());
		}
		return new DataFlowTemplateBuilder(uri);
	}

	private DataFlowTemplateBuilder(URI serverUri) {
		this.serverUri = serverUri;
	}

	public DataFlowTemplateBuilder skipSslValidation(boolean skipSslValidation) {
		this.skipSslValidation = skipSslValidation;
		return this;
	}

	public DataFlowTemplateBuilder restTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
		return this;
	}

	public DataFlowTemplate build() {
		return new DataFlowTemplate(this.serverUri, this.restTemplate == null ?
				new RestTemplateConfigurer().skipSslValidation(this.skipSslValidation)
				.configure()
				: this.restTemplate);
	}
}
