/*
 * Copyright 2016-2017 the original author or authors.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.dataflow.core.StreamAppDefinition;

/**
 * Builder of deployable SCDF stream definitions which honors elements of SCDF Shell DSL.
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * StreamDefinition.builder("TICKTOCK")
 *	 .definition("time | log")
 *	 .addProperty("app.log.log.expression", "'TICKTOCK - TIMESTAMP: '.concat(payload)")
 *	 .build();
 * }
 * </pre>
 *
 * @author Vinicius Carvalho
 * @author Oleg Zhurakousky
 */
public class StreamDefinition {

	private String name;

	private String streamDefinitionDsl;

	private Map<String, String> deploymentProperties = new HashMap<>();

	private Map<String, Application> applications = new HashMap<>();

	private StreamDefinition() {
	}

	private StreamDefinition(StreamDefinitionBuilder builder) {
		this.name = builder.name;
		this.streamDefinitionDsl = builder.streamDefinitionDsl;
		this.deploymentProperties = builder.deploymentProperties;
		for (StreamAppDefinition appDefinition : new org.springframework.cloud.dataflow.core.StreamDefinition(this.name,
				this.streamDefinitionDsl).getAppDefinitions()) {
			this.applications.put(appDefinition.getRegisteredAppName(),
					new Application(appDefinition.getRegisteredAppName()));
		}
	}

	/**
	 * Creates an instance of the builder for a named stream
	 * @return an instance of a builder that <b>only</b> supports definition of the stream
	 * (e.g., <code>builder.definition("time | log")</code>). Once the definition is
	 * provided the subsequent builder will make additional operations available (i.e.,
	 * add/set application and/or deployemnt properties)
	 */
	public static StreamDefinitionBuilder builder(String name) {
		return new StreamDefinitionBuilder(name);
	}

	public String getName() {
		return name;
	}

	public String getDefinition() {
		return streamDefinitionDsl;
	}

	public Map<String, String> getDeploymentProperties() {
		return deploymentProperties;
	}

	public Collection<Application> getApplications() {
		return applications.values();
	}

	public Application getApplication(String name) {
		return applications.get(name);
	}

	public interface StreamPropertiesBuilder {
		StreamPropertiesBuilder addProperty(String key, String value);

		StreamPropertiesBuilder addProperties(Map<String, String> properties);

		StreamDefinition build();
	}

	public static class StreamDefinitionBuilder {
		private final String name;

		private final Map<String, String> deploymentProperties = new HashMap<>();

		private String streamDefinitionDsl;

		private StreamDefinitionBuilder(String name) {
			this.name = name;
		}

		/**
		 * Sets the definition of this stream (e.g., <code>"time | log"</code>)
		 * @return an instance of a builder that <b>only</b> supports addition of
		 * application and/or deployment properties as well as the building of the actual
		 * {@link StreamDefinition}
		 */
		public StreamPropertiesBuilder definition(String streamDefinitionDsl) {
			this.streamDefinitionDsl = streamDefinitionDsl;
			return new StreamPropertiesBuilder() {

				@Override
				public StreamDefinition build() {
					return new StreamDefinition(StreamDefinitionBuilder.this);
				}

				@Override
				public StreamPropertiesBuilder addProperty(String key, String value) {
					StreamDefinitionBuilder.this.deploymentProperties.put(key, value);
					return this;
				}

				@Override
				public StreamPropertiesBuilder addProperties(Map<String, String> properties) {
					StreamDefinitionBuilder.this.deploymentProperties.putAll(properties);
					return this;
				}
			};
		}
	}
}
