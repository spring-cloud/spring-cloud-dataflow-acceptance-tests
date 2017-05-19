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
	private Map<String,StreamAppDefinition> applications = new HashMap<>();

	private StreamDefinition(){}

	private StreamDefinition(StreamDefinitionBuilder builder){
		this.name = builder.name;
		this.definition = builder.definition;
		this.deploymentProperties = builder.deploymentProperties;
		for(StreamAppDefinition appDefinition : new org.springframework.cloud.dataflow.core.StreamDefinition(name,definition).getAppDefinitions()){
			this.applications.put(appDefinition.getRegisteredAppName(),appDefinition);
		}
	}

	public static StreamDefinitionBuilder builder(String name){
		return new StreamDefinitionBuilder(name);
	}

	public Collection<StreamAppDefinition> getApplications(){
		return applications.values();
	}

	public StreamAppDefinition getApplication(String name){
		return applications.get(name);
	}

	static class StreamDefinitionBuilder {

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

//
//	private StreamDefinition(StreamDefinitionBuilder builder){
//		this.applications = builder.applications;
//		this.name = builder.name;
//		this.deploymentProperties = builder.deploymentProperties;
//	}
//
//	public static StreamDefinitionBuilder builder(String name){
//		return new StreamDefinitionBuilder(name);
//	}
//
//	private List<Application> applications = new LinkedList<>();
//	private String name;
//	private Map<String,String> deploymentProperties;
//
//	public String getName() {
//		return name;
//	}
//
//	public String getDefinition(){
//		return StringUtils.collectionToDelimitedString(applications," | ");
//	}
//
//	@Override
//	public String toString() {
//		final StringBuffer sb = new StringBuffer("StreamDefinition{");
//		sb.append("name='").append(name).append("'\n");
//		sb.append("definition='").append(getDefinition()).append("'\n");
//		sb.append('}');
//		return sb.toString();
//	}
//
//	public static class StreamDefinitionBuilder {
//
//		private List<Application> applications = new LinkedList<>();
//		private String name;
//		private Map<String,String> deploymentProperties = new HashMap();
//
//		public StreamDefinitionBuilder(String name) {
//			this.name = name;
//		}
//
//		public SourceDefinitionBuilder source(String definition){
//			return new SourceDefinitionBuilder(definition);
//		}
//
//		public void addProperty(String key, String value){
//			this.deploymentProperties.put(key, value);
//		}
//
//		public void addProperties(Map<String,String> deploymentProperties){
//			this.deploymentProperties.putAll(deploymentProperties);
//		}
//
//
//		class SourceDefinitionBuilder {
//
//			private Application application;
//
//			public SourceDefinitionBuilder(String definition){
//				this.application = new Application(definition);
//				StreamDefinitionBuilder.this.applications.add(this.application);
//			}
//
//			public StreamDefinition build(){
//				return new StreamDefinition(StreamDefinitionBuilder.this);
//			}
//
//			public ProcessorDefinitionBuilder processor(String definition){
//				return new ProcessorDefinitionBuilder(definition);
//			}
//
//			public SinkDefinitionBuilder sink(String definition){
//				return new SinkDefinitionBuilder(definition);
//			}
//
//			public SourceDefinitionBuilder addProperty(String key, String value){
//				StreamDefinitionBuilder.this.deploymentProperties.put(key, value);
//				return this;
//			}
//
//			public SourceDefinitionBuilder addProperties(Map<String,String> deploymentProperties){
//				StreamDefinitionBuilder.this.deploymentProperties.putAll(deploymentProperties);
//				return this;
//			}
//
//		}
//
//		class ProcessorDefinitionBuilder {
//
//			private Application application;
//
//			public ProcessorDefinitionBuilder(String definition){
//				this.application = new Application(definition);
//				StreamDefinitionBuilder.this.applications.add(this.application);
//			}
//
//			public ProcessorDefinitionBuilder processor(String definition){
//				return new ProcessorDefinitionBuilder(definition);
//			}
//
//			public SinkDefinitionBuilder sink(String definition){
//				return new SinkDefinitionBuilder(name);
//			}
//			public ProcessorDefinitionBuilder addProperty(String key, String value){
//				StreamDefinitionBuilder.this.deploymentProperties.put(key, value);
//				return this;
//			}
//
//			public ProcessorDefinitionBuilder addProperties(Map<String,String> deploymentProperties){
//				StreamDefinitionBuilder.this.deploymentProperties.putAll(deploymentProperties);
//				return this;
//			}
//		}
//
//		class SinkDefinitionBuilder {
//
//			private Application application;
//
//			public SinkDefinitionBuilder(String definition){
//				this.application = new Application(definition);
//				StreamDefinitionBuilder.this.applications.add(this.application);
//			}
//
//			public StreamDefinition build(){
//				return new StreamDefinition(StreamDefinitionBuilder.this);
//			}
//
//			public SinkDefinitionBuilder addProperty(String key, String value){
//				StreamDefinitionBuilder.this.deploymentProperties.put(key, value);
//				return this;
//			}
//
//			public SinkDefinitionBuilder addProperties(Map<String,String> deploymentProperties){
//				StreamDefinitionBuilder.this.deploymentProperties.putAll(deploymentProperties);
//				return this;
//			}
//		}
//
//	}

	public static void main(String[] args) {
		StreamDefinition definition = StreamDefinition.builder("httpLog")
				.definition("http --foo=bar | log").build();
		definition.getApplications();

	}






}
