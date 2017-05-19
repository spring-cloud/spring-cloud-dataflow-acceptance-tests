package org.springframework.cloud.dataflow.acceptance.test.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.dataflow.core.StreamAppDefinition;


/**
 * @author Vinicius Carvalho
 */
public class StreamDefinition {

	private String name;
	private String definition;
	private Map<String,String> deploymentProperties = new HashMap<>();
	private Map<String,Application> applications = new HashMap<>();

	private StreamDefinition(){}

	private StreamDefinition(StreamDefinitionBuilder builder){
		this.name = builder.name;
		this.definition = builder.definition;
		this.deploymentProperties = builder.deploymentProperties;
		for(StreamAppDefinition appDefinition : new org.springframework.cloud.dataflow.core.StreamDefinition(name,definition).getAppDefinitions()){
			this.applications.put(appDefinition.getRegisteredAppName(),new Application(appDefinition.getRegisteredAppName()));
		}
	}

	public String getName() {
		return name;
	}

	public String getDefinition() {
		return definition;
	}

	public Map<String, String> getDeploymentProperties() {
		return deploymentProperties;
	}

	public static StreamDefinitionBuilder builder(String name){
		return new StreamDefinitionBuilder(name);
	}

	public Collection<Application> getApplications(){
		return applications.values();
	}

	public Application getApplication(String name){
		return applications.get(name);
	}

	public static class StreamDefinitionBuilder {

		private String name;
		private String definition;
		private Map<String,String> deploymentProperties = new HashMap<>();

		public StreamDefinitionBuilder(String name){
			this.name = name;
		}

		public StreamDefinitionBuilder definition(String definition){
			this.definition = definition;
			return this;
		}

		public StreamDefinition build(){
			return new StreamDefinition(this);
		}

		public StreamDefinitionBuilder addProperty(String key, String value){
			this.deploymentProperties.put(key, value);
			return this;
		}

		public StreamDefinitionBuilder addProperties(Map<String,String> deploymentProperties){
			this.deploymentProperties = deploymentProperties;
			return this;
		}
	}

}
