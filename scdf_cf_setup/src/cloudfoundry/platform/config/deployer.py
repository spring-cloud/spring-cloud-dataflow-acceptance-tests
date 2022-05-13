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
import re

from cloudfoundry.platform.config.environment import EnvironmentAware

logger = logging.getLogger(__name__)


class CloudFoundryDeployerConfig(EnvironmentAware):
    prefix = "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_"
    url_key = prefix + "URL"
    org_key = prefix + "ORG"
    space_key = prefix + "SPACE"
    app_domain_key = prefix + "DOMAIN"
    username_key = prefix + "USERNAME"
    password_key = prefix + "PASSWORD"
    skip_ssl_validation_key = prefix + "SKIP_SSL_VALIDATION"
    scheduler_url_key = prefix + 'SCHEDULER_URL'
    required_keys = [url_key, org_key, space_key, username_key, password_key]

    @classmethod
    def assert_required_keys(cls, env):
        EnvironmentAware.assert_required_keys(cls,
                                              env,
                                              [cls.url_key,
                                               cls.org_key,
                                               cls.space_key,
                                               cls.username_key,
                                               cls.password_key])

    @classmethod
    def from_env_vars(cls, env=os.environ):
        env = cls.env_vars(env, cls.prefix)
        if not env.get(cls.url_key):
            cls.assert_required_keys(env)

        skip_ssl_validation = False
        if env.get(cls.skip_ssl_validation_key) and env.get(cls.skip_ssl_validation_key).lower() in ['true', 'y',
                                                                                                     'yes']:
            skip_ssl_validation = True

        return CloudFoundryDeployerConfig(api_endpoint=env.get(cls.url_key),
                                          org=env.get(cls.org_key),
                                          space=env.get(cls.space_key),
                                          app_domain=env.get(cls.app_domain_key),
                                          username=env.get(cls.username_key),
                                          password=env.get(cls.password_key),
                                          skip_ssl_validation=skip_ssl_validation,
                                          env=env)

    def __init__(self, api_endpoint, org, space, app_domain, username, password, skip_ssl_validation=True,
                 env=None):
        # Besides the required props,we will put these in the scdf_server manifest
        self.env = env
        self.api_endpoint = api_endpoint
        self.org = org
        self.space = space
        self.app_domain = app_domain
        self.username = username
        self.password = password
        self.skip_ssl_validation = skip_ssl_validation
        # Get if from the service-key
        self.scheduler_url = None
        self.env = env
        self.validate()

    """
     Most of the mapped attributes are for SPRING_APPLICATION_JSON, any that are not explicitly mapped go to the manifest as
     top level server env.
    """

    def trust_certs_host(self):
        return re.sub('^http[s]?://', '', self.api_endpoint)

    def uaa_host(self):
        return re.sub('^http[s]?://api', 'uaa', self.api_endpoint)

    def as_env(self, excluded=[]):
        env = {}

        if self.env:
            for k, v in self.env.items():
                if k not in excluded:
                    env[k] = v
        return env

    def connection(self):
        return {'url': self.api_endpoint, 'org': self.org, 'space': self.space, 'domain': self.app_domain,
                'username': self.username, 'password': self.password}

    def validate(self):
        if not self.api_endpoint:
            raise ValueError("'api_endpoint' is required")
        if not self.org:
            raise ValueError("'org' is required")
        if not self.space:
            raise ValueError("'space' is required")
        if not self.app_domain:
            raise ValueError("'app_domain' is required")
        if not self.username:
            raise ValueError("'username' is required")
        if not self.password:
            raise ValueError("'password' is required")
