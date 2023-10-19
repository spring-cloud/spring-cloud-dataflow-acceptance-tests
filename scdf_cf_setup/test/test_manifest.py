__copyright__ = '''
Copyright 2022 the original author or authors.
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
'''

__author__ = 'David Turanski'

import json
import logging
import os
import unittest

import yaml

from cloudfoundry.platform.standalone import deploy
from src import install
from cloudfoundry.cli import CloudFoundry
from cloudfoundry.platform.config.db import DatasourceConfig
from cloudfoundry.platform.config.skipper import SkipperConfig
from cloudfoundry.platform.config.dataflow import DataflowConfig
from cloudfoundry.platform.config.configuration import ConfigurationProperties
from cloudfoundry.platform.config.deployer import CloudFoundryDeployerConfig
from cloudfoundry.platform.config.service import CloudFoundryServicesConfig
from cloudfoundry.platform.config.installation import InstallationContext

import cloudfoundry.platform.manifest.skipper as skipper_manifest
import cloudfoundry.platform.manifest.dataflow as dataflow_manifest
from cloudfoundry.platform.manifest.util import spring_application_json

from install.shell import Shell

install.enable_debug_logging()

logger = logging.getLogger(__name__)


def indent(i):
    return ' ' * i


class TestManifest(unittest.TestCase):

    def test_spring_application_json(self):
        deployment = {'services': ['mysql']}
        platform_accounts_key = 'spring.cloud.dataflow.task.platform.cloudfoundry.accounts'
        saj = spring_application_json(installation=self.installation(), app_deployment=deployment,
                                      platform_accounts_key=platform_accounts_key)
        self.assertEqual({"remoteRepositories": {"repo0": {"url": "https://repo.spring.io/snapshot"}}},
                         saj['maven'])
        self.assertEqual({'url': 'https://api.mycf.org', 'org': 'org', 'space': 'space',
                          'domain': 'apps.mycf.org', 'username': 'user', 'password': 'password'},
                         saj[platform_accounts_key]['default']['connection'])
        self.assertEqual(
            saj['spring.cloud.dataflow.task.platform.cloudfoundry.accounts']['default']['deployment']['services'],
            ['mysql'])

    def test_basic_dataflow_manifest(self):
        params = {'skipper_uri': 'https://skipper-server.somehost.cf-app.com/api'}
        manifest = dataflow_manifest.create_manifest(installation=self.installation(), params=params)
        doc = yaml.safe_load(manifest)
        app = doc['applications'][0]
        self.assertEqual(app['name'], 'dataflow-server')
        self.assertEqual(app['buildpack'], 'java_buildpack_offline')
        self.assertEqual(app['path'], 'test/dataflow.jar')
        self.assertEqual(app['env']['SPRING_DATASOURCE_URL'], 'jdbc://oracle:thin:123.456.78:1234/xe/dataflow')
        self.assertEqual(app['env']['SPRING_CLOUD_SKIPPER_CLIENT_SERVER_URI'], params['skipper_uri'])
        self.assertEqual(app['services'], ['mysql'])
        saj = json.loads(app['env']['SPRING_APPLICATION_JSON'])
        self.assertEqual(
            saj['spring.cloud.dataflow.task.platform.cloudfoundry.accounts']['default']['deployment']['services'],
            ['mysql'])

    def test_dataflow_manifest_with_kafka(self):
        params = {'skipper_uri': 'https://skipper-server.somehost.cf-app.com/api'}
        installation = self.installation()
        installation.config_props.binder='kafka'
        config_props=installation.config_props
        manifest = dataflow_manifest.create_manifest(installation=installation, params=params)
        cf = CloudFoundry(config_props=config_props, deployer_config=installation.deployer_config,
                          shell=Shell(dry_run=True))
        dir = os.path.dirname(__file__)
        deploy(cf=cf, installation=installation, create_manifest=dataflow_manifest.create_manifest,
               application_name='dataflow-server',
               manifest_path=os.path.join(dir, 'test.yml'))

        doc = yaml.safe_load(manifest)
        app = doc['applications'][0]

    def test_basic_skipper_manifest(self):
        manifest = skipper_manifest.create_manifest(installation=self.installation())
        doc = yaml.safe_load(manifest)
        app = doc['applications'][0]
        self.assertEqual(app['name'], 'skipper-server')
        self.assertEqual(app['buildpack'], 'java_buildpack_offline')
        self.assertEqual(app['path'], 'test/skipper.jar')
        self.assertEqual(app['env']['SPRING_DATASOURCE_URL'], 'jdbc://oracle:thin:123.456.78:1234/xe/skipper')
        self.assertEqual(app['services'], ['mysql'])
        saj = json.loads(app['env']['SPRING_APPLICATION_JSON'])
        self.assertEqual(
            saj['spring.cloud.skipper.server.platform.cloudfoundry.accounts']['default']['deployment']['services'],
            ['rabbit'])

    def installation(self):
        deployer_env = {
            CloudFoundryDeployerConfig.scheduler_url_key:
                "'https://scheduler.sys.somehost.cf-app.com'",
            'SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_AUTO_DELETE_MAVEN_ARTIFACTS': 'false'
        }
        deployer_config = CloudFoundryDeployerConfig(api_endpoint="https://api.mycf.org",
                                                     org="org",
                                                     space="space",
                                                     app_domain="apps.mycf.org",
                                                     username="user",
                                                     password="password",
                                                     env=deployer_env
                                                     )
        config_props = ConfigurationProperties(
            dataflow_version='2.11.2-SNAPSHOT',
            skipper_version='2.11.2-SNAPSHOT',
            skipper_jar_path='test/skipper.jar',
            dataflow_jar_path='test/dataflow.jar',
            maven_repos={'repo0': 'https://repo.spring.io/snapshot'},
            platform='cloudfoundry',
            task_services=['mysql'],
            stream_services=['rabbit'])
        datasources_config = {
            'dataflow': DatasourceConfig(url="jdbc://oracle:thin:123.456.78:1234/xe/dataflow",
                                         username="test",
                                         password="password",
                                         driver_class_name="com.oracle.jdbc.OracleDriver",
                                         name="db"),
            'skipper': DatasourceConfig(url="jdbc://oracle:thin:123.456.78:1234/xe/skipper",
                                        username="test",
                                        password="password",
                                        driver_class_name="com.oracle.jdbc.OracleDriver",
                                        name="db")}

        install = InstallationContext(deployer_config=deployer_config,
                                      dataflow_config=DataflowConfig(),
                                      skipper_config=SkipperConfig(),
                                      services_config=CloudFoundryServicesConfig.defaults(),
                                      config_props=config_props)
        install.datasources_config = datasources_config
        return install
