/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.cloud.dataflow.acceptance.tests.support.Oracle;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper100;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper101;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper102;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper103;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper104;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper105;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper110;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper11x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper20x;

@ExtendWith(DockerComposeExtension.class)
@Oracle
@Bootstrap
public class SkipperServerOracleBootstrapTests extends AbstractDataflowTests {

	@Test
	@Skipper100
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper100oracle.yml" }, services = { "skipper" })
	public void testSkipper100WithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper101
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper101oracle.yml" }, services = { "skipper" })
	public void testSkipper101WithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper102
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper102oracle.yml" }, services = { "skipper" })
	public void testSkipper102WithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper103
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper103oracle.yml" }, services = { "skipper" })
	public void testSkipper103WithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper104
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper104oracle.yml" }, services = { "skipper" })
	public void testSkipper104WithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper105
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper105oracle.yml" }, services = { "skipper" })
	public void testSkipper105WithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper110
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper110oracle.yml" }, services = { "skipper" })
	public void testSkipper110WithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper11x
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper11xoracle.yml" }, services = { "skipper" })
	public void testSkipper11xWithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper20x
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/oracle.yml" }, services = { "oracle" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xoracle.yml" }, services = { "skipper" })
	public void testSkipper20xWithOracle(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}
}
