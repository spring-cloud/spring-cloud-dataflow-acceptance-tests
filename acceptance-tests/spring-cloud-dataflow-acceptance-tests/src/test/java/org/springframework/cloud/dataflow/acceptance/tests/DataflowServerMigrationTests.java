/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.cloud.dataflow.acceptance.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.dataflow.acceptance.core.DockerCompose;
import org.springframework.cloud.dataflow.acceptance.core.DockerComposeExtension;
import org.springframework.cloud.dataflow.acceptance.core.DockerComposeInfo;
import org.springframework.cloud.dataflow.acceptance.tests.support.DataflowAll;
import org.springframework.cloud.dataflow.acceptance.tests.support.Db2;
import org.springframework.cloud.dataflow.acceptance.tests.support.Migration;
import org.springframework.cloud.dataflow.acceptance.tests.support.MsSql;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql;
import org.springframework.cloud.dataflow.acceptance.tests.support.Oracle;
import org.springframework.cloud.dataflow.acceptance.tests.support.Postgres;

@ExtendWith(DockerComposeExtension.class)
@Migration
public class DataflowServerMigrationTests extends AbstractDataflowTests {

	@Test
	@Postgres
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/postgres.yml" }, services = { "postgres" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xpostgres.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflow/dataflow20xpostgres.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	public void testMigrationFrom173ToLatestWithPostgres(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	@Test
	@Mysql
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql.yml" }, services = { "mysql" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xmysql.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflow/dataflow20xmysql.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	public void testMigrationFrom173ToLatestWithMysql(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	@Test
	@Oracle
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xoracle.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflow/dataflow20xoracle.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	public void testMigrationFrom173ToLatestWithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	@Test
	@MsSql
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mssql.yml" }, services = { "mssql" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xmssql.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflow/dataflow20xmssql.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	public void testMigrationFrom173ToLatestWithMsSql(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	@Test
	@Db2
	@DataflowAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/db2.yml" }, services = { "db2" })
	@DockerCompose(id = "dataflow17x", order = 1, locations = { "src/test/resources/dataflow/dataflow17xdb2.yml" }, services = { "dataflow" }, log = "dataflow17x/")
	@DockerCompose(id = "dataflow20x", order = 2, locations = { "src/test/resources/dataflow/dataflow20xdb2.yml" }, services = { "dataflow" }, start = false, log = "dataflow20x/")
	public void testMigrationFrom173ToLatestWithDb2(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	private void migrationAsserts(DockerComposeInfo dockerComposeInfo) {
		// check 17x running
		assertDataflowServerRunning(dockerComposeInfo, "dataflow17x", "dataflow", false);
		// register stream/task apps and check we have something
		List<String> initialRegisterApps = registerApps(dockerComposeInfo, "dataflow17x", "dataflow");
		assertThat(initialRegisterApps.size()).isGreaterThan(0);
		// upgrade to 20x and check running
		upgradeDataflow(dockerComposeInfo, "dataflow17x", "dataflow20x", "dataflow");
		// check we still have same apps after upgrade
		List<String> migratedRegisterApps = registerApps(dockerComposeInfo, "dataflow20x", "dataflow");
		assertThat(migratedRegisterApps.size()).isGreaterThan(0);
		assertThat(initialRegisterApps).containsExactlyInAnyOrderElementsOf(migratedRegisterApps);
	}
}
