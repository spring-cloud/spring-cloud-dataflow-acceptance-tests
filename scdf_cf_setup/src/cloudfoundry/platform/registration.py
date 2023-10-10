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

import logging
import re
from string import Template
from os.path import exists
import json

logger = logging.getLogger(__name__)

__author__ = 'David Turanski'

import requests


def register_apps(cf, installation, server_uri, app_import_path='app-imports.properties'):
    app_registrations = AppRegistrations(cf, installation.config_props, server_uri=server_uri, app_import_path=app_import_path)
    if installation.dataflow_config.streams_enabled:
        app_registrations.register_stream_apps()
    else:
        logger.info("skipping stream apps, since streams_enabled if False")

    if installation.dataflow_config.tasks_enabled:
        app_registrations.register_task_apps()
    else:
        logger.info("skipping task apps, since tasks_enabled if False")
    app_registrations.register_test_apps()
    logger.debug("registered apps:\n%s" % json.dumps(app_registrations.apps(), indent=4))


class AppRegistrations:
    DEFAULT_DATAFLOW_VERSION = '2.11.1-SNAPSHOT'

    def __init__(self, cf, config_props, server_uri, app_import_path='app-imports.properties'):
        self.headers = headers = {'Authorization': cf.oauth_token()}
        self.app_import_path = app_import_path
        self.apps_url = "%s/apps" % server_uri
        self.task_apps_uri = config_props.task_apps_uri
        self.stream_apps_uri = config_props.stream_apps_uri
        self.binder = config_props.binder
        self.dataflow_version = config_props.dataflow_version
        if not self.dataflow_version:
            logger.warning(
                "'dataflow_version' is not defined in test configuration - using default: %s" % self.DEFAULT_DATAFLOW_VERSION)
            self.dataflow_version = self.DEFAULT_DATAFLOW_VERSION

    def register_stream_apps(self):
        logger.info("registering stream apps from %s" % self.stream_apps_uri)
        requests.post(url=self.apps_url, headers=self.headers, params={'uri': self.stream_apps_uri, 'force': True})
        # TODO add error handling when not success

    def register_task_apps(self):
        logger.info("registering task apps from %s" % self.task_apps_uri)
        requests.post(url=self.apps_url, params={'uri': self.task_apps_uri, 'force': True}, headers=self.headers)
        # TODO add error handling when not success

    def register_test_apps(self):
        logger.info("registering test apps from %s" % self.app_import_path)
        if exists(self.app_import_path):
            with open(self.app_import_path) as imports:
                app_registrations = imports.readlines()
                for app_reg in app_registrations:
                    if not app_reg.startswith('#') and len(app_reg.rstrip()) > 0:
                        app_name, app_type, uri, version = self.parse_app(app_reg)
                        logger.debug("registering app %s" % app_reg)
                        requests.post(url='%s/%s/%s/%s' % (self.apps_url, app_type, app_name, version),
                                      headers=self.headers,
                                      params={'uri': uri, 'force': True})
                        # TODO add error handling when not success
        else:
            logger.warning("app imports file for additional apps:%s does not exist" % self.app_import_path)

    def parse_app(self, data):
        valid_chars = '[a-zA-Z0-9/\_\:\-\$\.]+'
        pattern = '(%s)\.(%s)=(%s)' % (valid_chars, valid_chars, valid_chars)
        match = re.match(pattern, data)
        if match:
            app_type = match.group(1)
            app_name = (match.group(2))
            template = Template(match.group(3))
            uri = template.substitute({'BINDER': self.binder, 'DATAFLOW_VERSION': self.dataflow_version})
            version = (uri.split(':')[3])
            return app_name, app_type, uri, version
        else:
            raise ValueError("Unable to parse app registration %s" % data)

    def apps(self):
        r = requests.get(url=self.apps_url, headers=self.headers)
        if r.status_code != 200:
            logger.error("Unable to get registered apps")
            return None

        return r.json()
