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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class HttpPoster {

	private final RestTemplate restTemplate;

	public HttpPoster(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	Logger logger = LoggerFactory.getLogger(HttpPoster.class);

	public void httpPostData(URI uri, String message) {

		logger.info("posting to {}", uri);

		RequestEntity<String> requestEntity = RequestEntity
				.post(uri)
				.contentType(MediaType.TEXT_PLAIN)
				.body(message);

		ResponseEntity<?> responseEntity = restTemplate.exchange(requestEntity, Object.class);
		if (responseEntity.getStatusCode().isError()) {
			throw new RuntimeException(
					"HTTP POST " + message + "failed : Status code" + responseEntity.getStatusCode());
		}
	}

}
