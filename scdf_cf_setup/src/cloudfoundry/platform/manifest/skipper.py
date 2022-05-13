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
import random
from string import Template
from cloudfoundry.platform.manifest.util import format_saj, spring_application_json, format_yaml_list, format_env
from install.util import masked

logger = logging.getLogger(__name__)

manifest_template = '''
---
applications:
- name: $application_name
  host: $host_name
  memory: 2G
  disk_quota: 2G
  instances: 1
  buildpack: $buildpack
  path: $path

  env:
    SPRING_PROFILES_ACTIVE: cloud
    JBP_CONFIG_SPRING_AUTO_RECONFIGURATION: '{enabled: false}'    
    SPRING_APPLICATION_NAME: $application_name
    JBP_JRE_VERSION: '$jbp_jre_version'
    $app_config
    $datasource_config
    $top_level_deployer_properties
    $spring_application_json
  $services
'''


def create_manifest(installation, application_name='skipper-server', params={}):
    datasource_config = installation.datasources_config['skipper']
    logger.debug('datasource_config:' + masked(datasource_config))
    config_props = installation.config_props
    jar_path = config_props.skipper_jar_path
    server_services = [installation.services_config.get('sql').name] if installation.services_config.get('sql') else []
    app_deployment = {'services': config_props.stream_services,
                      'deleteRoutes': False,
                      'enableRandomAppNamePrefix': False,
                      'memory': 2048
                      }
    deployer_config = installation.deployer_config
    excluded_deployer_props = deployer_config.required_keys
    excluded_deployer_props.extend([deployer_config.skip_ssl_validation_key, deployer_config.scheduler_url_key])
    app_config = installation.skipper_config.as_env()
    saj = format_saj(spring_application_json(installation, app_deployment,
                                             'spring.cloud.skipper.server.platform.cloudfoundry.accounts'))
    template = Template(manifest_template)
    return template.substitute({
        'application_name': application_name,
        'host_name': "%s-%d" % (application_name, random.randint(0, 1000)),
        'buildpack': installation.config_props.buildpack,
        'path': jar_path,
        'app_config': format_env(app_config),
        'jbp_jre_version': installation.config_props.jbp_jre_version,
        'datasource_config': format_env(datasource_config.as_env()),
        'top_level_deployer_properties': format_env(
            installation.deployer_config.as_env(excluded=excluded_deployer_props)),
        'spring_application_json': saj,
        'services': "services:\n" + format_yaml_list(server_services) if server_services else ''})
