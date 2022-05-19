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

import logging
import os
import json

from cloudfoundry.platform.config.environment import EnvironmentAware

logger = logging.getLogger(__name__)


class ConfigurationProperties(EnvironmentAware):
    """
    """

    @classmethod
    def from_env_vars(cls, env=os.environ):
        kwargs = cls.set_if_present(env, ConfigurationProperties().__dict__, converters={
            'deploy_wait_sec': lambda x: int(x),
            'max_retries': lambda x: int(x),
            'maven_repos': lambda x: json.loads(x),
            'task_services': lambda x: x.split(','),
            'stream_services': lambda x: x.split(',')
        })
        config = ConfigurationProperties(**kwargs)
        return config

    def __init__(self,
                 platform='tile',
                 binder='rabbit',
                 dataflow_version=None,
                 skipper_version=None,
                 dataflow_jar_path='./build/dataflow-server.jar',
                 skipper_jar_path='./build/skipper-server.jar',
                 deploy_wait_sec=20,
                 max_retries=60,  # 20 min max wait time for a service or app to come up
                 buildpack='java_buildpack_offline',
                 maven_repos={'repo1': 'https://repo.spring.io/libs-snapshot'},
                 jbp_jre_version="{ jre: { version: 1.8.+ }}",
                 config_server_enabled=False,
                 task_services=['mysql'],
                 stream_services=['rabbit'],
                 task_apps_uri='https://dataflow.spring.io/task-maven-latest',
                 cert_host=None,
                 service_key_name='scdf_cf_setup'
                 ):
        self.platform = platform
        self.binder = binder
        self.dataflow_version = dataflow_version
        self.skipper_version = skipper_version
        self.dataflow_jar_path = dataflow_jar_path
        self.skipper_jar_path = skipper_jar_path
        self.deploy_wait_sec = deploy_wait_sec
        self.max_retries = max_retries
        self.buildpack = buildpack
        self.maven_repos = maven_repos
        self.jbp_jre_version = jbp_jre_version
        self.config_server_enabled = config_server_enabled
        self.task_services = task_services
        self.stream_services = stream_services
        self.cert_host = cert_host
        self.service_key_name = service_key_name

        if self.binder == 'rabbit':
            self.stream_apps_uri = 'https://dataflow.spring.io/rabbitmq-maven-latest'
        elif self.binder == 'kafka':
            self.stream_apps_uri = 'https://dataflow.spring.io/kafka-maven-latest'
