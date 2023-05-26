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

import unittest
from cloudfoundry.platform.config.db import DBConfig
from cloudfoundry.platform.config.configuration import ConfigurationProperties
from cloudfoundry.platform.config.deployer import CloudFoundryDeployerConfig
from cloudfoundry.platform.config.service import CloudFoundryServicesConfig, ServiceConfig
from cloudfoundry.platform.config.kafka import KafkaConfig
from cloudfoundry.platform.config.installation import InstallationContext
import logging
from src import install

install.enable_debug_logging()

logger = logging.getLogger(__name__)

class TestConfigProperties(unittest.TestCase):


    def test_services_config_default(self):
        env = {}
        cf_services_config = CloudFoundryServicesConfig.from_env_vars(env)
        self.assertEqual(ServiceConfig.rabbit_default(), cf_services_config['rabbit'])
        self.assertEqual(ServiceConfig.scheduler_default(), cf_services_config['scheduler'])
        self.assertEqual(ServiceConfig.sql_default(), cf_services_config['sql'])
        self.assertEqual(ServiceConfig.dataflow_default(), cf_services_config['dataflow'])
        self.assertEqual(ServiceConfig.config_default(), cf_services_config['config'])

    def test_cf_at_config_standalone(self):
        with self.assertRaises(ValueError):
            InstallationContext.from_env_vars({'PLATFORM': 'cloudfoundry'})
        with self.assertRaises(ValueError):
            InstallationContext.from_env_vars(merged_env([deployer_env(), {'PLATFORM': 'cloudfoundry'}]))
        with self.assertRaises(ValueError):
            InstallationContext.from_env_vars(standalone_test_env())

        InstallationContext.from_env_vars(merged_env([deployer_env(), standalone_test_env()]))

    def test_env_present_test_config(self):
        test_config = ConfigurationProperties.from_env_vars(env={'DATAFLOW_VERSION': '2.11.0-SNAPSHOT',
                                                               'SKIPPER_VERSION': '2.11.0-SNAPSHOT',
                                                               'DEPLOY_WAIT_SEC': '60',
                                                               'MAX_RETRIES': '10'})
        self.assertEqual(60, test_config.deploy_wait_sec)
        self.assertEqual(10, test_config.max_retries)
        self.assertEqual(test_config.maven_repos['repo1'], 'https://repo.spring.io/snapshot')

    def test_assert_required_keys(self):
        with self.assertRaises(ValueError):
            DBConfig.assert_required_keys({})
        with self.assertRaises(ValueError):
            CloudFoundryDeployerConfig.assert_required_keys({})
        with self.assertRaises(ValueError):
            KafkaConfig.assert_required_keys({})

    def test_kafka_settings(self):
        env = standalone_test_env().copy()
        env.update({'BINDER': 'kafka'})
        test_config = ConfigurationProperties.from_env_vars(env)
        self.assertEqual("https://dataflow.spring.io/kafka-maven-latest", test_config.stream_apps_uri)


def deployer_env():
    return {'SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL': 'https://api.sys.some-host.cf.app.com',
            'SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG': 'org',
            'SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE': 'space',
            'SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN': 'apps.some-host.cf.app.com',
            'SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME': 'user',
            'SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD': 'password',
            'SPRING_CLOUD_DEPLOYER_SKIP_SSL_VALIDATION': 'false',
            'SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SCHEDULER_URL': 'scheduler.sys.some-host.cf.app.com'
            }


def deployer_config():
    return CloudFoundryDeployerConfig.from_env_vars(deployer_env())


def standalone_test_env():
    return {'DATAFLOW_VERSION': '2.11.0-SNAPSHOT',
            'SKIPPER_VERSION': '2.11.0-SNAPSHOT',
            'PLATFORM': 'cloudfoundry'}


def standalone_test_config():
    return ConfigurationProperties.from_env_vars(standalone_test_env())


def merged_env(envs):
    merged = {}
    for env in envs:
        merged |= env
    return merged
