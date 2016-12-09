/*
 * Copyright 2016 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.dataflow.acceptance.test.util;

/**
 * Methods that help retrieve app uri information for a specific platform.
 * @author Glenn Renfro
 */
public interface UriHelper {

	/**
	 * Identifies and sets the uri for each app in a stream.
	 * @param stream the stream to augment.
	 */
	public void setUrisForStream(Stream stream);
}
