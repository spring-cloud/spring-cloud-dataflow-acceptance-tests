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
import sys

from cloudfoundry.cli import CloudFoundry
from optparse import OptionParser
from cloudfoundry.platform import standalone, tile
from cloudfoundry.platform.config.installation import InstallationContext
from cloudfoundry.platform.config.dataflow import DataflowConfig
from cloudfoundry.platform.config.db import DatasourceConfig
from cloudfoundry.platform.config.deployer import CloudFoundryDeployerConfig
from install import enable_debug_logging

logger = logging.getLogger(__name__)


def cf_config_from_env():
    deployer_config = CloudFoundryDeployerConfig.from_env_vars()
    db_config = DatasourceConfig.from_spring_env_vars()
    dataflow_config = DataflowConfig.from_env_vars()

    return InstallationContext(deployer_config=deployer_config, db_config=db_config,
                               dataflow_config=dataflow_config)


def clean(args):
    parser = OptionParser()
    parser.usage = "%prog clean options"

    parser.add_option('-v', '--debug',
                      help='debug level logging',
                      dest='debug', default=False, action='store_true')
    parser.add_option('--appsOnly',
                      help='run the cleanup for the apps, but excluding services',
                      dest='apps_only', action='store_true')
    try:
        options, arguments = parser.parse_args(args)
        if options.debug:
            enable_debug_logging()
        installation = InstallationContext.from_env_vars()
        cf = CloudFoundry.connect(deployer_config=installation.deployer_config, config_props=installation.config_props)
        if not options.apps_only:
            logger.info("deleting current services...")
            services = cf.services()
            for service in services:
                if cf.service_key(service.name, installation.config_props.service_key_name):
                    cf.delete_service_key(service.name, installation.config_props.service_key_name)
                cf.delete_service(service.name)
        else:
            logger.info("'apps-only' option is set, keeping existing current services")
        logger.info("cleaning apps")
        cf.delete_apps()
        if installation.config_props.platform == "tile":
            return tile.clean(cf, installation)
        elif installation.config_props.platform == "cloudfoundry":
            return standalone.clean(cf, installation)
        else:
            logger.error("invalid platform type %s should be in [cloudfoundry,tile]" % installation.config_props.platform)

    except SystemExit:
        parser.print_help()
        exit(1)


if __name__ == '__main__':
    clean(sys.argv)
