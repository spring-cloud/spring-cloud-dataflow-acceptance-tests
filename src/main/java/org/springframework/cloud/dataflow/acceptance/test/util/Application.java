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
 * Contains the metadata required for the acceptance test execute calls against
 * the application's end points.
 *
 * @author Glenn Renfro
 */
public class Application {

	protected String url;
	protected String definition;

	/**
	 * Initializes the application instance.
	 *
	 * @param definition Establish the registered app name and the
	 * required properties.
	 */
	public Application(String definition) {
		this.definition = definition;
	}

	/**
	 * Retrieve the definition for this application.
	 * @return the definition
	 */
	public String getDefinition () {
		return this.definition;
	}

	/**
	 * Retrieve the name of the app for this definition.
	 * @return name of app
	 */
	public String getName () {
		if (definition.trim().contains(" ")) {
			return definition.trim().substring(0, definition.indexOf(" "));
		}
		return this.definition;
	}

	/**
	 * The host where the application can be reached.
	 * @param url
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * Retrieve the uri for this application.
	 */
	public String getUrl() {
		return this.url;
	}

	public String toString() {
		return this.definition;
	}
}
