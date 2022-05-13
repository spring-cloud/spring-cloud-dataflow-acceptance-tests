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

import install
from cloudfoundry.platform.config.db import DBConfig
from install.db import init_db

install.enable_debug_logging()


class DBTestCase(unittest.TestCase):
    def test_db_config(self):
        db_config = postgres_env()
        self.assertEqual('scdf1234', db_config.dataflow_db_name)
        self.assertEqual('skipper5678', db_config.skipper_db_name)
        self.assertEqual('system_password', db_config.system_password)
        self.assertEqual('system_user', db_config.system_username)
        self.assertEqual('host', db_config.host)
        self.assertEqual(5432, db_config.port)

    def test_postgres_datasource_config(self):
        datasources = init_db(postgres_env())
        self.assertEqual('user', datasources['skipper'].username)
        self.assertEqual('password', datasources['skipper'].password)
        self.assertEqual('jdbc:postgresql://host:5432/skipper5678?user=user&password=password',
                         datasources['skipper'].url)

        self.assertEqual('user', datasources['dataflow'].username)
        self.assertEqual('password', datasources['dataflow'].password)
        self.assertEqual('jdbc:postgresql://host:5432/scdf1234?user=user&password=password',
                         datasources['dataflow'].url)

        env = datasources['skipper'].as_env()
        self.assertEqual('"jdbc:postgresql://host:5432/skipper5678?user=user&password=password"',
                         env.get('SPRING_DATASOURCE_URL'))
        self.assertEqual('user', env.get('SPRING_DATASOURCE_USERNAME'))
        self.assertEqual('password', env.get('SPRING_DATASOURCE_PASSWORD'))
        self.assertEqual('org.postgresql.Driver', env.get('SPRING_DATASOURCE_DRIVER_CLASS_NAME'))

        db_config = postgres_env({'SQL_SKIPPER_DB_NAME': ''})
        datasources = init_db(db_config)
        self.assertEqual(db_config.dataflow_db_name, db_config.skipper_db_name)
        self.assertEqual('scdf1234', db_config.dataflow_db_name)
        self.assertEqual(datasources['dataflow'].url, datasources['skipper'].url)
        self.assertEqual('jdbc:postgresql://host:5432/scdf1234?user=user&password=password', datasources['skipper'].url)

    def test_oracle_datasource_config(self):
        datasources = init_db(oracle_env())
        self.assertEqual('skipper5678', datasources['skipper'].username)
        self.assertEqual('password', datasources['skipper'].password)
        self.assertEqual('jdbc:oracle:thin:@host:1521:exeeeee', datasources['skipper'].url)

        self.assertEqual('scdf1234', datasources['dataflow'].username)
        self.assertEqual('password', datasources['dataflow'].password)
        self.assertEqual('jdbc:oracle:thin:@host:1521:exeeeee', datasources['dataflow'].url)


def postgres_env(test_env={}):
    env = {
        'SQL_PROVIDER': 'postgresql',
        'SQL_HOST': 'host',
        'SQL_PORT': 5432,
        'SQL_PASSWORD': 'password',
        'SQL_USERNAME': 'user',
        'SQL_SYSTEM_USERNAME': 'system_user',
        'SQL_SYSTEM_PASSWORD': 'system_password',
        'SQL_DATAFLOW_DB_NAME': 'scdf1234',
        'SQL_SKIPPER_DB_NAME': 'skipper5678'
    }
    env.update(test_env)
    return DBConfig.from_env_vars(env)


def oracle_env(test_env={}):
    env = {
        'SQL_PROVIDER': 'oracle',
        'SQL_HOST': 'host',
        'SQL_PORT': 1521,
        'SQL_PASSWORD': 'password',
        'SQL_SERVICE_NAME': 'exeeeee',
        'SQL_SYSTEM_USERNAME': 'system_username',
        'SQL_SYSTEM_PASSWORD': 'system_password',
        'SQL_DATAFLOW_DB_NAME': 'scdf1234',
        'SQL_SKIPPER_DB_NAME': 'skipper5678'
    }
    env.update(test_env)
    return DBConfig.from_env_vars(env)


if __name__ == '__main__':
    unittest.main()
