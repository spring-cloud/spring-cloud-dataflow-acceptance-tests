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

import subprocess
import unittest

from cloudfoundry.cli import CloudFoundry
from cloudfoundry.platform.config.dataflow import DataflowConfig
from cloudfoundry.platform.config.deployer import CloudFoundryDeployerConfig
from cloudfoundry.platform.config.configuration import ConfigurationProperties
from cloudfoundry.platform.config.service import ServiceConfig
from cloudfoundry.platform.config.installation import InstallationContext

from install.shell import Shell


class MockShell:
    def __init__(self):
        self.dry_run = True

    def stdout_to_s(self, proc):
        return """
Getting key %s for service instance %s as admin...

{
     "api_endpoint": "https://scheduler.sys.somehost.cf-app.com"
}
            """ % ('ci-scheduler', 'scdf-at')

    def exec(self, cmd):
        return subprocess.CompletedProcess([], 0)


class TestCommands(unittest.TestCase):
    def test_service_key(self):
        cf = CloudFoundry(self.installation().deployer_config, self.installation().config_props, MockShell())
        service_key = cf.service_key('ci-scheduler', 'scdf-at')
        self.assertEqual('https://scheduler.sys.somehost.cf-app.com', service_key.get( 'api_endpoint'))

    def test_basic_shell(self):
        shell = Shell()
        p = shell.exec("ls -l")
        self.assertEqual(p.returncode, 0)
        self.assertEqual(['ls', '-l'], p.args)
        shell.log_stdout(p)

    def test_target(self):
        cf = self.cloudfoundry()
        p = cf.target(org='p-dataflow', space='dturanski')
        self.assertEqual(['cf', 'target', '-o', 'p-dataflow', '-s', 'dturanski'], p.args)

    def test_push(self):
        cf = self.cloudfoundry()
        p = cf.push("-f scdf-server.yml")
        self.assertEqual(['cf', 'push', '-f', 'scdf-server.yml'], p.args)

    def test_login(self):
        cf = self.cloudfoundry()
        p = cf.login()
        self.assertEqual(['cf', 'login', '-a', 'https://api.mycf.org', '-o', 'org', '-s', 'space',
                          '-u', 'user', '-p', 'password', '--skip-ssl-validation'], p.args)

    def test_delete_all(self):
        cf = self.cloudfoundry()
        apps = ['scdf-app-repo', 'skipper-server-1411', 'dataflow-server-19655', 'LKg7lBB-taphttp-log-v1',
                'LKg7lBB-taphttp-http-v1', 'LKg7lBB-tapstream-log-v1']

        apps.remove('scdf-app-repo')

        cf.delete_apps(apps)

    def test_create_service(self):
        cf = self.cloudfoundry()
        p = cf.create_service(service_config=ServiceConfig(name="rabbit", service="p.rabbitmq", plan="single-node"))
        self.assertEqual(['cf', 'create-service', 'p.rabbitmq', 'single-node', 'rabbit'], p.args)

    def cloudfoundry(self):
        installation = self.installation()
        return CloudFoundry(deployer_config=installation.deployer_config, config_props=installation.config_props,
                            shell=Shell(dry_run=True))

    def installation(self):
        deployer_config = CloudFoundryDeployerConfig(api_endpoint="https://api.mycf.org",
                                                     org="org",
                                                     space="space",
                                                     app_domain="apps.mycf.org",
                                                     username="user",
                                                     password="password")
        config_props = ConfigurationProperties()
        config_props.dataflow_version = '2.11.1-SNAPSHOT'
        config_props.skipper_version = '2.11.1-SNAPSHOT'
        config_props.platform = 'cloudfoundry'
        return InstallationContext(deployer_config=deployer_config,
                                   config_props=config_props,
                                   services_config={'sql': ServiceConfig(name='foo', plan='bar', service='service')},
                                   dataflow_config=DataflowConfig())
