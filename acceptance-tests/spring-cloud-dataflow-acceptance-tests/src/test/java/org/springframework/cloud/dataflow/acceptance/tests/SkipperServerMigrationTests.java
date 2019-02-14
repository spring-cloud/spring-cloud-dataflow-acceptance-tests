/*
 * Copyright 2018-2019 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.dataflow.acceptance.core.DockerCompose;
import org.springframework.cloud.dataflow.acceptance.core.DockerComposeExtension;
import org.springframework.cloud.dataflow.acceptance.core.DockerComposeInfo;
import org.springframework.cloud.dataflow.acceptance.tests.support.Db2;
import org.springframework.cloud.dataflow.acceptance.tests.support.Migration;
import org.springframework.cloud.dataflow.acceptance.tests.support.MsSql;
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql;
import org.springframework.cloud.dataflow.acceptance.tests.support.Oracle;
import org.springframework.cloud.dataflow.acceptance.tests.support.Postgres;
import org.springframework.cloud.dataflow.acceptance.tests.support.SkipperAll;

/**
 * Tests going through start of skipper servers with databases and verifying
 * that newer versions work when older versions have created initial db schemas.
 *
 * @author Janne Valkealahti
 *
 */
@ExtendWith(DockerComposeExtension.class)
@Migration
public class SkipperServerMigrationTests extends AbstractDataflowTests {

	@Test
	@Postgres
	@SkipperAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/postgres.yml" }, services = { "postgres" })
	@DockerCompose(id = "skipper11x", order = 1, locations = { "src/test/resources/skipper/skipper11xpostgres.yml" }, services = { "skipper" }, log = "skipper11x/")
	@DockerCompose(id = "skipper20x", order = 1, locations = { "src/test/resources/skipper/skipper20xpostgres.yml" }, services = { "skipper" }, start = false, log = "skipper20x/")
	public void testMigrationFrom11xToLatestWithPostgres(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	@Test
	@Mysql
	@SkipperAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper11x", order = 1, locations = { "src/test/resources/skipper/skipper11xmysql.yml" }, services = { "skipper" }, log = "skipper11x/")
	@DockerCompose(id = "skipper20x", order = 1, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" }, start = false, log = "skipper20x/")
	public void testMigrationFrom11xToLatestWithMysql(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	@Test
	@Oracle
	@SkipperAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper11x", order = 1, locations = { "src/test/resources/skipper/skipper11xoracle.yml" }, services = { "skipper" }, log = "skipper11x/")
	@DockerCompose(id = "skipper20x", order = 1, locations = { "src/test/resources/skipper/skipper20xoracle.yml" }, services = { "skipper" }, start = false, log = "skipper20x/")
	public void testMigrationFrom11xToLatestWithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}


	@Test
	@MsSql
	@SkipperAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mssql.yml" }, services = { "mssql" })
	@DockerCompose(id = "skipper11x", order = 1, locations = { "src/test/resources/skipper/skipper11xmssql.yml" }, services = { "skipper" }, log = "skipper11x/")
	@DockerCompose(id = "skipper20x", order = 1, locations = { "src/test/resources/skipper/skipper20xmssql.yml" }, services = { "skipper" }, start = false, log = "skipper20x/")
	public void testMigrationFrom11xToLatestWithMsSql(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}


	@Test
	@Db2
	@SkipperAll
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/db2.yml" }, services = { "db2" })
	@DockerCompose(id = "skipper11x", order = 1, locations = { "src/test/resources/skipper/skipper11xdb2.yml" }, services = { "skipper" }, log = "skipper11x/")
	@DockerCompose(id = "skipper20x", order = 1, locations = { "src/test/resources/skipper/skipper20xdb2.yml" }, services = { "skipper" }, start = false, log = "skipper20x/")
	public void testMigrationFrom11xToLatestWithDb2(DockerComposeInfo dockerComposeInfo) throws Exception {
		migrationAsserts(dockerComposeInfo);
	}

	private void migrationAsserts(DockerComposeInfo dockerComposeInfo) {
		assertSkipperServerRunning(dockerComposeInfo, "skipper11x", "skipper");
		upgradeSkipper(dockerComposeInfo, "skipper11x", "skipper20x", "skipper");
	}
}
