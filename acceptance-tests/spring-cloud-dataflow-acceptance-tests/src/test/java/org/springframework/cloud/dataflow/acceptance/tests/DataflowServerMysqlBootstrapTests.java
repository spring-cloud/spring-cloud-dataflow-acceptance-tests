/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.dataflow.acceptance.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.dataflow.acceptance.core.DockerCompose;
import org.springframework.cloud.dataflow.acceptance.core.DockerComposeExtension;
import org.springframework.cloud.dataflow.acceptance.core.DockerComposeInfo;
import org.springframework.cloud.dataflow.acceptance.tests.support.Bootstrap;
import org.springframework.cloud.dataflow.acceptance.tests.support.Dataflow17x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Dataflow20x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Dataflow21x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Dataflow22x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Dataflow23x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql_5_6;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql_5_7;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql_8_0;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper11x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper20x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper22x;

@ExtendWith(DockerComposeExtension.class)
@Mysql
@Bootstrap
public class DataflowServerMysqlBootstrapTests extends AbstractDataflowTests {

	@Test
	@Skipper11x
	@Dataflow17x
	@Mysql_5_6
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_6.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper11xmysql.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow17xmysql.yml" }, services = { "dataflow" })
	public void testDataflow17xWithMysql56(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");
	}

	@Test
	@Skipper20x
	@Dataflow21x
	@Mysql_5_6
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_6.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow21xmysql.yml" }, services = { "dataflow" })
	public void testDataflow21xWithMysql56(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
	}

	@Test
	@Skipper20x
	@Dataflow21x
	@Mysql_5_7
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_7.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow21xmysql.yml" }, services = { "dataflow" })
	public void testDataflow21xWithMysql57(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
	}

	@Test
	@Skipper20x
	@Dataflow21x
	@Mysql_8_0
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_8_0.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmysqlwithmysqldriver.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow21xmysqlwithmysqldriver.yml" }, services = { "dataflow" })
	public void testDataflow21xWithMysql80(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
	}

	@Test
	@Skipper20x
	@Dataflow22x
	@Mysql_5_6
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_6.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow22xmysql.yml" }, services = { "dataflow" })
	public void testDataflow22xWithMysql56(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
		deleteBatchJobExecutions(dockerComposeInfo, "dataflow", "dataflow");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS, 0, 0);
	}

	@Test
	@Skipper20x
	@Dataflow22x
	@Mysql_5_7
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_7.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow22xmysql.yml" }, services = { "dataflow" })
	public void testDataflow22xWithMysql57(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
		deleteBatchJobExecutions(dockerComposeInfo, "dataflow", "dataflow");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS, 0, 0);
	}

	@Test
	@Skipper20x
	@Dataflow22x
	@Mysql_8_0
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_8_0.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmysqlwithmysqldriver.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow22xmysqlwithmysqldriver.yml" }, services = { "dataflow" })
	public void testDataflow22xWithMysql80(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
		deleteBatchJobExecutions(dockerComposeInfo, "dataflow", "dataflow");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS, 0, 0);
	}

	@Test
	@Skipper20x
	@Dataflow20x
	@Mysql_5_6
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_6.yml" }, services = { "mysql" })
	@DockerCompose(id = "dataflow", order = 1, locations = { "src/test/resources/dataflow/dataflow20xmysql.yml" }, services = { "dataflow" })
	@DockerCompose(id = "skipper", order = 2, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" }, start = false)
	public void testDataflow20xBeforeSkipperMysql56(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow", false);
		start(dockerComposeInfo, "skipper");
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper11x
	@Dataflow17x
	@Mysql_5_6
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_6.yml" }, services = { "mysql" })
	@DockerCompose(id = "dataflow17x", order = 2, locations = { "src/test/resources/dataflow/dataflow17xmysql.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 3, locations = { "src/test/resources/dataflow/dataflow20xmysql.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	@DockerCompose(id = "dataflow21x", order = 4, locations = { "src/test/resources/dataflow/dataflow21xmysql.yml" }, services = { "dataflow" }, start = false, log = "dataflow21x/")
	public void testDataflow20xAfter17xWithMysql56(DockerComposeInfo dockerComposeInfo) throws Exception {
		// github.com/spring-cloud/spring-cloud-dataflow/issues/2903
		assertDataflowServerRunning(dockerComposeInfo, "dataflow17x", "dataflow", false);

		List<String> initialRegisterApps = registerApps(dockerComposeInfo, "dataflow17x", "dataflow");
		assertThat(initialRegisterApps.size()).isGreaterThan(0);

		List<String> initialRegisterStreams = registerStreamDefs(dockerComposeInfo, "dataflow17x", "dataflow");
		assertThat(initialRegisterStreams.size()).isGreaterThan(0);

		stop(dockerComposeInfo, "dataflow17x");
		start(dockerComposeInfo, "dataflow20x");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow20x", "dataflow", false);

		stop(dockerComposeInfo, "dataflow20x");
		start(dockerComposeInfo, "dataflow21x");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow21x", "dataflow", false);
	}


	@Test
	@Skipper22x
	@Dataflow23x
	@Mysql_5_6
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_6.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper22xmysql.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow23xmysql.yml" }, services = { "dataflow" })
	public void testDataflow23xWithMysql56(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
		deleteBatchJobExecutions(dockerComposeInfo, "dataflow", "dataflow");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS, 0, 0);
	}

	@Test
	@Skipper22x
	@Dataflow23x
	@Mysql_5_7
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_7.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper22xmysql.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow23xmysql.yml" }, services = { "dataflow" })
	public void testDataflow23xWithMysql57(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
		deleteBatchJobExecutions(dockerComposeInfo, "dataflow", "dataflow");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS, 0, 0);
	}

	@Test
	@Skipper22x
	@Dataflow23x
	@Mysql_8_0
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_8_0.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper22xmysqlwithmysqldriver.yml" }, services = { "skipper" })
	@DockerCompose(id = "dataflow", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow23xmysqlwithmysqldriver.yml" }, services = { "dataflow" })
	public void testDataflow23xWithMysql80(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow", "dataflow");

		registerBatchApp(dockerComposeInfo, "dataflow", "dataflow");
		registerBatchTaskDefs(dockerComposeInfo, "dataflow", "dataflow");
		launchTask(dockerComposeInfo, "dataflow", "dataflow", "fakebatch");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
		deleteBatchJobExecutions(dockerComposeInfo, "dataflow", "dataflow");
		waitBatchJobExecution(dockerComposeInfo, "dataflow", "dataflow", "COMPLETED", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS, 0, 0);
	}
}
