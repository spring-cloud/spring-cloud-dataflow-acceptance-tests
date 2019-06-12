/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.cloud.dataflow.acceptance.tests.support.DataflowAll;
import org.springframework.cloud.dataflow.acceptance.tests.support.Db2;
import org.springframework.cloud.dataflow.acceptance.tests.support.Migration;
import org.springframework.cloud.dataflow.acceptance.tests.support.MsSql;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql_5_6;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql_5_7;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql_8_0;
import org.springframework.cloud.dataflow.acceptance.tests.support.Oracle;
import org.springframework.cloud.dataflow.acceptance.tests.support.Postgres;

/**
 * Essentially we're starting dataflow 17x assuming classic mode, register
 * stream/task apps, create ticktock stream and timestamp taks, upgrade to
 * skipper 20x and dataflow 20x and check that we still have same stuff visible.
 * For now this is a minimal test scenario without actually running stream or
 * task.
 *
 * @author Janne Valkealahti
 *
 */
@ExtendWith(DockerComposeExtension.class)
@Migration
public class DataflowServerMigrationTests extends AbstractDataflowTests {

	// postgres

	@Test
	@Postgres
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/postgres.yml" }, services = { "postgres" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xpostgres.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow20xpostgres.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	@DockerCompose(id = "skipper", order = 3, locations = { "src/test/resources/skipper/skipper20xpostgres.yml" }, services = { "skipper" }, start = false)
	public void testMigrationFrom173ToLatestWithPostgres(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	// mysql

	@Test
	@Mysql_5_6
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_6.yml" }, services = { "mysql" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xmysql.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow20xmysql.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	@DockerCompose(id = "skipper", order = 3, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" }, start = false)
	public void testMigrationFrom173ToLatestWithMysql56(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	@Test
	@Mysql_5_7
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_5_7.yml" }, services = { "mysql" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xmysql.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow20xmysql.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	@DockerCompose(id = "skipper", order = 3, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" }, start = false)
	public void testMigrationFrom173ToLatestWithMysql57(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	@Test
	@Mysql_8_0
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql_8_0.yml" }, services = { "mysql" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xmysql.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow20xmysql.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	@DockerCompose(id = "skipper", order = 3, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" }, start = false)
	public void testMigrationFrom173ToLatestWithMysql80(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	// oracle

	@Test
	@Oracle
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xoracle.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow20xoracle.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	@DockerCompose(id = "skipper", order = 3, locations = { "src/test/resources/skipper/skipper20xoracle.yml" }, services = { "skipper" }, start = false)
	public void testMigrationFrom173ToLatestWithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	// mssql

	@Test
	@MsSql
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mssql.yml" }, services = { "mssql" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xmssql.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow20xmssql.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	@DockerCompose(id = "skipper", order = 3, locations = { "src/test/resources/skipper/skipper20xmssql.yml" }, services = { "skipper" }, start = false)
	public void testMigrationFrom173ToLatestWithMsSql(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	// db2

	@Test
	@Db2
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/db2.yml" }, services = { "db2" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xdb2.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflowandskipper/dataflow20xdb2.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	@DockerCompose(id = "skipper", order = 3, locations = { "src/test/resources/skipper/skipper20xdb2.yml" }, services = { "skipper" }, start = false)
	public void testMigrationFrom173ToLatestWithDb2(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	private void migrationAsserts(DockerComposeInfo dockerComposeInfo) {
		// check 17x running
		assertDataflowServerRunning(dockerComposeInfo, "dataflow17x", "dataflow", false);

		// register stream/task apps and check we have something
		List<String> initialRegisterApps = registerApps(dockerComposeInfo, "dataflow17x", "dataflow");
		assertThat(initialRegisterApps.size()).isGreaterThan(0);

		// register ticktock stream
		List<String> initialRegisterStreams = registerStreamDefs(dockerComposeInfo, "dataflow17x", "dataflow");
		assertThat(initialRegisterStreams.size()).isGreaterThan(0);

		// register timestamp task
		List<String> initialRegisterTasks = registerTaskDefs(dockerComposeInfo, "dataflow17x", "dataflow");
		assertThat(initialRegisterTasks.size()).isGreaterThan(0);

		// check audit records
		List<String> initialAuditRecords = auditRecords(dockerComposeInfo, "dataflow17x", "dataflow");
		// 17x vs 20x will have different recorces, so we can only check that we created something
		assertThat(initialAuditRecords.size()).isGreaterThan(0);

		// upgrade to 20x and check running
		// start skipper as it's needed for streams even if we don't launch anything
		stop(dockerComposeInfo, "dataflow17x");
		start(dockerComposeInfo, "skipper");
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
		start(dockerComposeInfo, "dataflow20x");
		assertDataflowServerRunning(dockerComposeInfo, "dataflow20x", "dataflow");

		// we upgraded from 17x classic to 20x

		// check we still have same apps after upgrade
		List<String> migratedRegisterApps = registeredApps(dockerComposeInfo, "dataflow20x", "dataflow");
		assertThat(migratedRegisterApps.size()).isGreaterThan(0);
		assertThat(initialRegisterApps).containsExactlyInAnyOrderElementsOf(migratedRegisterApps);

		// check migrated stream defs
		List<String> migratedRegisterStreams = registeredStreamDefs(dockerComposeInfo, "dataflow20x", "dataflow");
		assertThat(migratedRegisterStreams.size()).isGreaterThan(0);
		assertThat(initialRegisterStreams).containsExactlyInAnyOrderElementsOf(migratedRegisterStreams);

		// check migrated task defs
		List<String> migratedRegisterTasks = registeredTaskDefs(dockerComposeInfo, "dataflow20x", "dataflow");
		assertThat(migratedRegisterTasks.size()).isGreaterThan(0);
		assertThat(initialRegisterTasks).containsExactlyInAnyOrderElementsOf(migratedRegisterTasks);

		// check migrated audit records
		List<String> migratedAuditRecords = auditRecords(dockerComposeInfo, "dataflow20x", "dataflow");
		assertThat(migratedAuditRecords.size()).isGreaterThan(0);
		assertThat(initialAuditRecords).containsExactlyInAnyOrderElementsOf(migratedAuditRecords);

		// register app manually and check that we got new registration
		// mostly to check that all the crazyness around hibernate_sequence works
		List<String> newRegisterApps = registerApp(dockerComposeInfo, "dataflow20x", "dataflow");
		assertThat(newRegisterApps.size()).isGreaterThan(migratedRegisterApps.size());

		// check that ticktock stream deploy registered before migration
		// doesn't throw error
		deployStream(dockerComposeInfo, "dataflow20x", "dataflow", "ticktock");
		waitStream(dockerComposeInfo, "dataflow20x", "dataflow", "ticktock", "deployed", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
		unDeployStream(dockerComposeInfo, "dataflow20x", "dataflow", "ticktock");
		waitStream(dockerComposeInfo, "dataflow20x", "dataflow", "ticktock", "undeployed", 1, TimeUnit.SECONDS, 180,
				TimeUnit.SECONDS);
	}
}
