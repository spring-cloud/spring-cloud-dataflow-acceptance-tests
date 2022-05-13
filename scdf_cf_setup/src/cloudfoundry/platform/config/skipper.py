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


class SkipperConfig(EnvironmentAware):
    prefix = 'SPRING_CLOUD_SKIPPER_'

    @classmethod
    def from_env_vars(cls, env=os.environ):
        env = cls.env_vars(env, cls.prefix)
        return SkipperConfig(env)

    def __init__(self, env={}):
        self.env = env

    def as_env(self):
        return self.env
