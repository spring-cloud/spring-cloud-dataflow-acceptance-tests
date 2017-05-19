package org.springframework.cloud.dataflow.acceptance.test;

import java.net.URI;

import org.junit.Test;

import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;
import org.springframework.hateoas.PagedResources;

/**
 * @author Vinicius Carvalho
 */
public class DataFlowOperationsTest {

	@Test
	public void streamOperations() throws Exception {
		DataFlowTemplate template = new DataFlowTemplate(new URI("http://localhost:9393"));
		AppStatusResource resource = template.runtimeOperations().status("httplog.http");
		PagedResources results = template.streamOperations().list();
		resource.getInstances();
	}

}
