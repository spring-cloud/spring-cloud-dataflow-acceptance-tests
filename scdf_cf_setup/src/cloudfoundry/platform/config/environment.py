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
from cloudfoundry.domain import JSonEnabled

logger = logging.getLogger(__name__)


class EnvironmentAware(JSonEnabled):
    @classmethod
    def set_if_present(cls, env, obj_as_dict, converters={}):
        default_converter = converters.get("*")
        kwargs = {}
        for key in obj_as_dict.keys():
            val = env.get(key.upper())
            if val:
                converter = converters.get(key) if converters.get(key) else default_converter
                kwargs[key] = converter(val) if converter else val
        return kwargs

    @classmethod
    def env_vars(cls, env, prefix):
        if not prefix:
            logger.warning("no environment variable prefix is set")
        if not prefix:
            return env
        prefixed_env = {}
        for key, value in env.items():
            if key.startswith(prefix):
                prefixed_env[key] = value
        return prefixed_env

    @classmethod
    def required_env_names(cls, names):
        s = ''
        for i in range(0, len(names)):
            s += names[i]
            if i < len(names) - 1:
                s = s + '\n'
        return s

    @classmethod
    def assert_required_keys(cls, target, env, required_keys):
        for key in required_keys:
            if not env.get(key):
                raise ValueError(
                    "A required environment variable is missing. The following required keys are bound to %s\n%s" % (
                        target.__name__, cls.required_env_names(required_keys)))
