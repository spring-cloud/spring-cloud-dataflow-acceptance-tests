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
import shutil

from os.path import exists

from install.shell import Shell
from install.util import masked

logger = logging.getLogger(__name__)


def client_credentials_from_service_key(cf, service_name, key_name):
    service_key = cf.create_service_key(service_name, key_name)
    return {
        'SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_TOKEN_URI': service_key['access-token-url'],
        'SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_ID': service_key['client-id'],
        'SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_SECRET': service_key['client-secret'],
        'SPRING_CLOUD_DATAFLOW_CLIENT_SERVER_URI': service_key['dataflow-url'],
        'SERVER_URI': service_key['dataflow-url']
    }


def setup(cf, installation):
    service_name = installation.services_config['dataflow'].name
    key_name = installation.config_props.service_key_name
    return client_credentials_from_service_key(cf, service_name, key_name)


def configure_dataflow_service(installation):
    logger.info("configuring dataflow tile")
    dataflow_tile_configuration = {'maven-cache': True}
    #
    # TODO: It does appear that you can pass any native properties this way, but this is undocumented AFAIK
    #
    dataflow_tile_configuration.update(installation.dataflow_config.as_env())
    if installation.dataflow_config.schedules_enabled:
        scheduler = installation.services_config['scheduler']
        dataflow_tile_configuration.update({'scheduler': {'name': scheduler.name, 'plan': scheduler.plan}})
    if installation.db_config:
        if installation.dataflow_config.streams_enabled:
            dataflow_tile_configuration['skipper-relational'] = user_provided(
                installation.datasources_config.get('skipper'))
        if installation.dataflow_config.tasks_enabled:
            dataflow_tile_configuration['relational-data-service'] = user_provided(
                installation.datasources_config.get('dataflow'))
    logger.debug("dataflow_tile_configuration:\n%s" % masked(dataflow_tile_configuration))
    return dataflow_tile_configuration


def user_provided(datasource_config):
    return {'user-provided':
                {'uri': datasource_config.url.replace('jdbc:', ''),
                 'jdbcUrl': datasource_config.url,
                 'username': datasource_config.username,
                 'password': datasource_config.password,
                 'dbname': datasource_config.name
                 }
            }


def clean(cf, installation):
    pass
