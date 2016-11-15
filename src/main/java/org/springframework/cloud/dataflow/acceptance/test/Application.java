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
 *
 */

package org.springframework.cloud.dataflow.acceptance.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.SocketUtils;

/**
 * Contains the metadata required for the acceptance test execute calls against
 * the application's end points.
 *
 * @author Glenn Renfro
 */
public class Application {

	protected String host;
	protected int port;
	protected String logLocation;
	protected String definition;

	/**
	 * Initializes the application instance.
	 * @param port - the port to use for this application.  If 0 a random
	 * available port is chosen.
	 * @param logLocation - the location where the the logs will be written
	 * for this application.
	 * @param definition Establish the registered app name and the
	 * required properties.
	 */
	public Application(int port, String logLocation, String definition) {
		if(port == 0) {
			this.port = SocketUtils.findAvailableTcpPort();
		} else {
			this.port = port;
		}
		this.logLocation = logLocation;
		setDefinition(definition);
	}

	/**
	 * Establish the registered app name and the required properties.
	 * @param definition
	 */
	public void setDefinition (String definition) {
		this.definition = String.format("%s --logging.file=%s --server.port=%s",
				definition, this.logLocation, this.port);
	}

	/**
	 * Retrieve the definition for this application.
	 * @return
	 */
	public String getDefinition () {
		return this.definition;
	}

	/**
	 * The host where the application can be reached.
	 * @param host
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * Retrieve the host for this application.
	 * @return
	 */
	public String getHost() {
		return this.host;
	}

	/**
	 * Retrieve the port that this application is listening.
	 * @return
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * Set the port that this application will receive requests.
	 * @param port
	 */
	public void setPort(int port) {
		this.port = port;
	}

	public String toString() {
		return this.definition;
	}
}
