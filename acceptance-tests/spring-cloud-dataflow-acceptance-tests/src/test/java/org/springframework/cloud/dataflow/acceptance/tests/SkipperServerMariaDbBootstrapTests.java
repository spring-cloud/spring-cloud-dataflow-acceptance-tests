/*
 * Copyright 2019 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.dataflow.acceptance.core.DockerCompose;
import org.springframework.cloud.dataflow.acceptance.core.DockerComposeExtension;
import org.springframework.cloud.dataflow.acceptance.core.DockerComposeInfo;
import org.springframework.cloud.dataflow.acceptance.tests.support.Bootstrap;
import org.springframework.cloud.dataflow.acceptance.tests.support.MariaDb;
import org.springframework.cloud.dataflow.acceptance.tests.support.MariaDb_10_1;
import org.springframework.cloud.dataflow.acceptance.tests.support.MariaDb_10_2;
import org.springframework.cloud.dataflow.acceptance.tests.support.MariaDb_10_3;
import org.springframework.cloud.dataflow.acceptance.tests.support.MariaDb_10_4;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper20x;

@ExtendWith(DockerComposeExtension.class)
@MariaDb
@Bootstrap
public class SkipperServerMariaDbBootstrapTests extends AbstractDataflowTests {

	@Test
	@Skipper20x
    @MariaDb_10_1
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mariadb_10_1.yml" }, services = { "mariadb" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmariadb.yml" }, services = { "skipper" })
	public void testSkipper20xWithMariaDb101(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper20x
    @MariaDb_10_2
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mariadb_10_2.yml" }, services = { "mariadb" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmariadb.yml" }, services = { "skipper" })
	public void testSkipper20xWithMariaDb102(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper20x
    @MariaDb_10_3
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mariadb_10_3.yml" }, services = { "mariadb" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmariadbwitholddialect.yml" }, services = { "skipper" })
	public void testSkipper20xWithMariaDb103(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper20x
    @MariaDb_10_4
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mariadb_10_4.yml" }, services = { "mariadb" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmariadbwitholddialect.yml" }, services = { "skipper" })
	public void testSkipper20xWithMariaDb104(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}
}
