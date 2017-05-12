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

package org.springframework.cloud.dataflow.acceptance.test.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.util.Assert;

/**
 * Manages the apps of a stream during the execution of a acceptance test.
 * This is done establishing the configuration such that the actuator end points
 * for each app are accessible.
 *
 * @author Glenn Renfro
 */
public class Stream {

	private String streamName;

	private Application source;

	private Application sink;

	private Map<String, Application> processors;

	private String definition;


	/**
	 * Initialize the stream instance.
	 * @param streamName the name of the stream.
	 * logs.
	 */
	public Stream(String streamName){
		Assert.hasText(streamName, "streamName must not be empty nor null");
		this.streamName = streamName;
		this.processors = new HashMap<>();
	}

	/**
	 * Sets the registered app name for the sink
	 *
	 * @param definition the registered app name
	 */
	public void setSink(String definition) {;
		sink = new Application(definition);
	}

	/**
	 * Retrieve the current sink
	 * @return the current sink.
	 */
	public Application getSink() {
		return sink;
	}

	/**
	 * Sets the registered app name for the source.
	 *
	 * @param definition the registered app name
	 */
	public void setSource(String definition) {
		source = new Application(definition);
	}

	/**
	 * Retrieve the current source.
	 * @return the current source.
	 */
	public Application getSource() {
		return source;
	}

	/**
	 * Establish the definition of the stream.
	 * @param definition stream definition.
	 */
	public void setDefinition(String definition) {
		this.definition = definition;
	}

	/**
	 * Retrieve the current stream definition.
	 * @return the stream definition.
	 */
	public String getDefinition() {
		return definition;
	}

	/**
	 * Retrieve the current stream name.
	 * @return the current stream name.
	 */
	public String getStreamName() {
		return streamName;
	}

	/**
	 * Add processor and its properties to a list of processors.  The
	 * default log location will be added to the processor app properties.
	 * @param processorName the key to retrieve the processor Name.
	 * @param definition the registered app name and its properties for the
	 * processor.
	 */
	public void addProcessor(String processorName, String definition) {
		int processorCount = processors.size();
		Application processor = new Application(definition);
		this.processors.put(processorName, processor);
	}

	/**
	 * Retrieve a unmodifiable list of the available processors.
	 */
	public Map<String, Application> getProcessors() {
		return Collections.unmodifiableMap(processors);
	}
}
