/*
 * Copyright 2019 the original author or authors.
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
import org.springframework.cloud.dataflow.acceptance.tests.support.Mysql;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper11x;
import org.springframework.cloud.dataflow.acceptance.tests.support.Skipper20x;

@ExtendWith(DockerComposeExtension.class)
@Mysql
@Bootstrap
public class SkipperServerMysqlBootstrapTests extends AbstractDataflowTests {

	@Test
	@Skipper11x
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper11xmysql.yml" }, services = { "skipper" })
	public void testSkipper11xWithMysql(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}

	@Test
	@Skipper20x
	@DockerCompose(id = "db", order = 0, locations = { "src/test/resources/db/mysql.yml" }, services = { "mysql" })
	@DockerCompose(id = "skipper", order = 1, locations = { "src/test/resources/skipper/skipper20xmysql.yml" }, services = { "skipper" })
	public void testSkipper20xWithMysql(DockerComposeInfo dockerComposeInfo) throws Exception {
		assertSkipperServerRunning(dockerComposeInfo, "skipper", "skipper");
	}
}
