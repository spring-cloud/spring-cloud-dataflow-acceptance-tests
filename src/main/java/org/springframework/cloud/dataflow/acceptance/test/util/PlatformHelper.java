/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.acceptance.test.util;

import java.util.Map;

/**
 * Methods that help retrieve app uri information for a specific platform.
 * @author Glenn Renfro
 * @author Thomas Risberg
 * @author Vinicius Carvalho
 */
public interface PlatformHelper {

	/**
	 * Identifies and sets the uri for each app in a stream.
	 * @param stream the stream to augment.
	 * @return whether URIs were available or not
	 */
	boolean setUrlsForStream(StreamDefinition stream);

	/**
	 * An opportunity to add platform specific deployment properties.
	 * @param stream the stream to augment.
	 * @param properties the map to add properties to.
	 */
	void addDeploymentProperties(StreamDefinition stream, Map<String, String> properties);

	/**
	 * Returns the logfile name to use.
	 */
	String getLogfileName();
}
