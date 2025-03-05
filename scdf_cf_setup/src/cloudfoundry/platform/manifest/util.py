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
import json
import re
import os

logger = logging.getLogger(__name__)


def spring_application_json(installation, app_deployment, platform_accounts_key):
    logger.info("generating spring_application_json for platform_accounts_key %s" % platform_accounts_key)
    logger.info("deployment config %s" % str(app_deployment))
    artifactoryUsername = os.environ['ARTIFACTORY_USERNAME']
    artifactoryPassword = os.environ['ARTIFACTORY_PASSWORD']
    saj = {
        "maven": {
            "remote-repositories": {
              "spring-commercial-snapshots": {
                "url": "https://repo.spring.io/artifactory/spring-commercial-snapshot-remote",
                "auth": {
                  "username": artifactoryUsername,
                  "password": artifactoryPassword
                }
              },
              "spring-commercial-releases": {
                "url": "https://repo.spring.io/artifactory/spring-commercial-release-remote",
                "auth": {
                  "username": artifactoryUsername,
                  "password": artifactoryPassword
                }
              }
            }
        },
        platform_accounts_key: {
            "default": {'connection': installation.deployer_config.connection(), 'deployment': app_deployment}
        }
    }
    return saj

def format_saj(application_json):
    saj = ''
    for k, v in application_json.items():
        for line in json.dumps({k: v}, indent=1).split('\n'):
            match = re.match('^(\s*)(.*)', line)
            if match.group(1):
                indent = ' ' * (4 * (len(match.group(1)) + 2))
                # 1 leading space must be top level element.
                if line == ' }':
                    line = line + ' ,'
                saj = saj + "%s%s\n" % (indent, line)
    # Remove trailing comma
    if saj.endswith(',\n'):
        saj = saj[0:-2]
    indent = ' ' * 8
    return "SPRING_APPLICATION_JSON: |-\n%s{\n%s\n%s}" % (indent, saj, indent)


def format_yaml_list(items, indent=4):
    s = ""
    i = len(items)
    for item in items:
        i = i - 1
        s = s + "%s- %s" % (' ' * indent, item)
        if i > 0:
            s = s + '\n'
    return s


def format_env(env, delim=': '):
    s = ""
    i = 0
    for k, v in env.items():
        spaces_not_tabs = ' ' * 4 if i > 0 else ''
        format_str = "%s%s%s%s" if i == len(env) - 1 else "%s%s%s%s\n"
        s = s + format_str % (spaces_not_tabs, k, delim, v)
        i = i + 1
    return s
