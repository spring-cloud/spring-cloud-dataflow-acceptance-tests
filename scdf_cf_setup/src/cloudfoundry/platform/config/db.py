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
from cloudfoundry.domain import JSonEnabled
from install.util import masked

logger = logging.getLogger(__name__)


class Provider:
    oracle_keys = ['oracle']
    postgresql_keys = ['postgresql', 'postgres']
    all_keys = oracle_keys + postgresql_keys

    def __init__(self, p):
        if not p in Provider.all_keys:
            raise ValueError("provider '%s' is unsupported or missing" % str(p))
        self.p = p

    def is_postrgesql(self):
        return self.p in Provider.postgresql_keys

    def is_oracle(self):
        return self.p in Provider.oracle_keys


class DBConfig(EnvironmentAware):
    prefix = "SQL_"
    provider_key = prefix + 'PROVIDER'
    host_key = prefix + 'HOST'
    port_key = prefix + 'PORT'
    username_key = prefix + 'USERNAME'
    password_key = prefix + 'PASSWORD'
    dataflow_db_name_key = prefix + 'DATAFLOW_DB_NAME'
    skipper_db_name_key = prefix + 'SKIPPER_DB_NAME'
    service_name_key = prefix + 'SERVICE_NAME'
    system_username_key = prefix + 'SYSTEM_USERNAME'
    system_password_key = prefix + 'SYSTEM_PASSWORD'

    @classmethod
    def assert_required_keys(cls, env):
        EnvironmentAware.assert_required_keys(cls,
                                              env,
                                              [cls.provider_key,
                                               cls.host_key,
                                               cls.port_key,
                                               cls.password_key,
                                               cls.dataflow_db_name_key,
                                               cls.system_username_key,
                                               cls.system_password_key
                                               ])

    @classmethod
    def from_env_vars(cls, env=os.environ):
        env = cls.env_vars(env, cls.prefix)
        if not env.get(cls.prefix + 'PROVIDER'):
            logger.warning("%s is not defined in the OS environment" % (cls.prefix + 'PROVIDER'))
            return None

        return DBConfig(host=env.get(cls.host_key),
                        port=env.get(cls.port_key),
                        username=env.get(cls.username_key),
                        password=env.get(cls.password_key),
                        provider=env.get(cls.provider_key),
                        dataflow_db_name=env.get(cls.dataflow_db_name_key),
                        skipper_db_name=env.get(cls.skipper_db_name_key),
                        service_name=env.get(cls.service_name_key),
                        system_username=env.get(cls.system_username_key),
                        system_password=env.get(cls.system_password_key)
                        )

    def __init__(self, host, port, username, password, provider, dataflow_db_name, system_username, system_password,
                 skipper_db_name=None, service_name=None):
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.provider = Provider(provider)
        self.dataflow_db_name = dataflow_db_name
        self.skipper_db_name = skipper_db_name
        self.service_name = service_name
        self.system_username = system_username
        self.system_password = system_password

        if not self.skipper_db_name:
            self.skipper_db_name = self.dataflow_db_name
        logger.debug(masked(self))

        if self.provider.is_oracle() and not self.service_name:
            raise ValueError("oracle DBConfig requires property '%s'" % self.service_name_key)
        if self.provider.is_postrgesql() and not self.username:
            raise ValueError("postgresql DBConfig requires property '%s'" % self.username_key)


class DatasourceConfig(JSonEnabled):
    """
    Not configured in environment. Built by init_db if using an external DB
    """

    prefix = "SPRING_DATASOURCE_"
    url_key = prefix + "URL"
    username_key = prefix + "USERNAME"
    password_key = prefix + "PASSWORD"
    driver_class_name_key = prefix + 'DRIVER_CLASS_NAME'

    def __init__(self, name, url, username, password, driver_class_name):
        self.name = name
        self.url = url
        self.username = username
        self.password = password
        self.driver_class_name = driver_class_name
        self.validate()

    def validate(self):
        if not self.name:
            raise ValueError("'name' is required")
        if not self.url:
            raise ValueError("'url' is required")
        if not self.username:
            raise ValueError("'username' is required")
        if not self.password:
            raise ValueError("'password' is required")
        if not self.driver_class_name:
            raise ValueError("'driver_class_name' is required")

    def as_env(self):
        return {DatasourceConfig.url_key: '"%s"' % self.url,
                DatasourceConfig.username_key: self.username,
                DatasourceConfig.password_key: self.password,
                DatasourceConfig.driver_class_name_key: self.driver_class_name}
