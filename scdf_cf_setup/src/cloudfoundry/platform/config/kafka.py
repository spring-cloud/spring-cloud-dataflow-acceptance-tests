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


class KafkaConfig(EnvironmentAware):
    prefix = "KAFKA_"
    broker_address_key = prefix + 'BROKER_ADDRESS'
    username_key = prefix + 'USERNAME'
    password_key = prefix + 'PASSWORD'

    @classmethod
    def assert_required_keys(cls, env):
        EnvironmentAware.assert_required_keys(cls, env, [cls.broker_address_key, cls.username_key, cls.password_key])

    @classmethod
    def from_env_vars(cls, env=os.environ):
        env = cls.env_vars(env, cls.prefix)
        if not env.get(cls.broker_address_key):
            logger.debug(
                "%s is not defined in the environment. Skipping Kafka config" % (cls.broker_address_key))
            return None

        return KafkaConfig(broker_address=env.get(cls.broker_address_key),
                           username=env.get(cls.username_key),
                           password=env.get(cls.password_key))

    def __init__(self, broker_address, username, password):
        self.broker_address = broker_address
        self.username = username
        self.password = password
        self.validate()

    def validate(self):
        if not self.broker_address:
            raise ValueError("'broker_address' is required")
        if not self.username:
            raise ValueError("'username' is required")
        if not self.password:
            raise ValueError("'password' is required")

    def as_env(self):
        return {KafkaConfig.broker_address_key: '"%s"' % self.broker_address,
                KafkaConfig.username_key: self.username,
                KafkaConfig.password_key: self.password,
                }
