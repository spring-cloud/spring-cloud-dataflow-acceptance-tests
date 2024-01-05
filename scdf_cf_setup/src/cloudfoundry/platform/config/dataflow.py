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
from cloudfoundry.platform.config.environment import EnvironmentAware

logger = logging.getLogger(__name__)


class DataflowConfig(EnvironmentAware):
    prefix = 'SPRING_CLOUD_DATAFLOW_'
    streams_enabled_key = prefix + 'FEATURES_STREAMS_ENABLED'
    tasks_enabled_key = prefix + 'FEATURES_TASKS_ENABLED'
    schedules_enabled_key = prefix + 'FEATURES_SCHEDULES_ENABLED'

    @classmethod
    def from_env_vars(cls, env=os.environ):
        env = cls.env_vars(env, cls.prefix)
        kwargs = cls.set_if_present(env, DataflowConfig().__dict__, {
            'streams_enabled': lambda x: x.lower() in ['true', 'y', 'yes'],
            'tasks_enabled': lambda x: x.lower() in ['true', 'y', 'yes'],
            'schedules_enabled': lambda x: x.lower() in ['true', 'y', 'yes']
        })
        config = DataflowConfig(**kwargs)
        return config

    def __init__(self,
                 streams_enabled=True,
                 tasks_enabled=True,
                 schedules_enabled=False,
                 env={}):
        self.streams_enabled = streams_enabled
        self.tasks_enabled = tasks_enabled
        self.schedules_enabled = schedules_enabled
        self.env = env
        self.validate()
        self.kafka_binder_configuration = {}
        self.oracle_configuration = {}
        self.trust_certs_configuration = {}

    def validate(self):
        if not self.streams_enabled and not self.tasks_enabled:
            raise ValueError("One 'streams_enabled' or 'tasks_enabled' must be true")
        if self.schedules_enabled and not self.tasks_enabled:
            raise ValueError("'schedules_enabled' requires 'tasks_enabled' to be true")

    def as_env(self):
        env = self.env.copy()
        env.update({DataflowConfig.streams_enabled_key: self.streams_enabled,
                    DataflowConfig.tasks_enabled_key: self.tasks_enabled,
                    DataflowConfig.schedules_enabled_key: self.schedules_enabled
                    })
        env.update(self.kafka_binder_configuration)
        env.update(self.oracle_configuration)
        env.update(self.trust_certs_configuration)
        return env

    def add_oracle_application_properties(self):
        # A work around for ComposedTaskRunner with Oracle, other tasks ignore
        self.oracle_configuration = {
            'spring.cloud.dataflow.applicationProperties.task.transactionIsolationLevel':
                'ISOLATION_READ_COMMITTED'}

    def add_trust_certs_application_properties(self, trust_certs):
        self.trust_certs_configuration = {}
        if self.streams_enabled:
            self.trust_certs_configuration.update({
                'spring.cloud.dataflow.applicationProperties.stream.trustCerts': trust_certs})
        if self.tasks_enabled:
            self.trust_certs_configuration.update({
                'spring.cloud.dataflow.applicationProperties.task.trustCerts': trust_certs})

    def add_kafka_application_properties(self, kafka_config):
        logger.debug('configuring kafka binder')
        if not kafka_config:
            raise ValueError("'kafka_config' is required")

        kafka_binder_key = 'spring.cloud.dataflow.applicationProperties.stream.spring.cloud.stream.kafka.binder.'
        env = {
            kafka_binder_key + 'brokers': kafka_config.broker_address,
            kafka_binder_key + 'jaas.loginModule': 'org.apache.kafka.common.security.scram.ScramLoginModule',
            kafka_binder_key + 'jaas.options.username': kafka_config.username,
            kafka_binder_key + 'jaas.options.password': kafka_config.password,
            kafka_binder_key + 'configuration.security.protocol': 'SASL_PLAINTEXT',
            kafka_binder_key + 'configuration.sasl.mechanism': 'SCRAM-SHA-512'
        }
        self.kafka_binder_configuration = env
