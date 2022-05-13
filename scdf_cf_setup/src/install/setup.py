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
import json

from cloudfoundry.cli import CloudFoundry
from optparse import OptionParser
from cloudfoundry.platform import standalone, tile
from cloudfoundry.platform.config.installation import InstallationContext
from cloudfoundry.platform.config.service import ServiceConfig
from install import enable_debug_logging
from install.db import init_db
from cloudfoundry.platform.registration import register_apps
from install.util import masked, setup_certs

logger = logging.getLogger(__name__)


def add_options_for_platform(parser, platform):
    # No domain related options here, use environment variables for everything.
    parser.add_option('-v', '--debug',
                      help='debug level logging',
                      dest='debug', action='store_true')
    parser.add_option('--initializeDB',
                      help='enable external DB initialization',
                      dest='initialize_db', action='store_true')
    if platform == 'cloudfoundry':
        parser.add_option('-d', '--doNotDownload',
                          help='skip the downloading of the SCDF/Skipper servers',
                          dest='do_not_download', action='store_true')


def setup(args):
    parser = OptionParser()
    parser.usage = "%prog setup options"

    try:

        installation = InstallationContext.from_env_vars()
        add_options_for_platform(parser, installation.config_props.platform)
        options, arguments = parser.parse_args(args)
        if options.debug:
            enable_debug_logging()

        logger.debug("Setup using config:\n%s" % masked(installation))

        cf = CloudFoundry.connect(deployer_config=installation.deployer_config,
                                  config_props=installation.config_props)

        # Initialize database
        if installation.db_config:
            installation.datasources_config = init_db(installation.db_config, options.initialize_db)

        # Schreduler applies to any platform
        if installation.services_config.get('scheduler'):
            ensure_required_services(cf, dict(
                filter(lambda entry: entry[0] == 'scheduler', installation.services_config.items())))
            logger.debug("getting scheduler_url from service_key")
            service_name = installation.services_config['scheduler'].name
            key_name = installation.config_props.service_key_name
            service_key = cf.create_service_key(service_name, key_name)
            installation.deployer_config.scheduler_url = service_key['url']
            cf.delete_service_key(service_key, key_name)

        if installation.config_props.platform == "tile":
            installation.services_config['dataflow'].config = tile.configure_dataflow_service(installation)

        ensure_required_services(cf, installation.services_config)

        if installation.config_props.platform == "tile":
            runtime_properties = tile.setup(cf, installation)
        elif installation.config_props.platform == "cloudfoundry":
            runtime_properties = standalone.setup(cf, installation, options.do_not_download)
        else:
            logger.error(
                "invalid platform type %s should be in [cloudfoundry,tile]" % installation.config_props.platform)

        dataflow_uri = runtime_properties['SPRING_CLOUD_DATAFLOW_CLIENT_SERVER_URI']
        setup_certs(installation.config_props.cert_host)
        # register_apps(cf, installation, dataflow_uri)
        return runtime_properties
    except SystemExit:
        parser.print_help()
        exit(1)


def ensure_required_services(cf, services_config):
    logger.info("verifying availability of required services:" + str([str(s) for s in services_config]))

    services = cf.services()
    required_services = {'create': [], 'wait': [], 'failed': [], 'deleting': [], 'unknown': []}

    for required_service in services_config.values():
        if required_service not in [ServiceConfig.of_service(service) for service in services]:
            logger.debug("Adding %s to required services" % masked(required_service))
            required_services['create'].append(required_service)
        else:
            logger.debug("Checking health of required service %s" % masked(required_service))
            for service in services:
                if ServiceConfig.of_service(service) == required_service:
                    if service.status not in ['create succeeded', 'update succeeded']:
                        logger.warning(
                            "status of required service %s is %s" % (service.name, service.status))
                        if service.status == 'create in progress':
                            required_services['wait'].append(service)
                        elif service.status == 'delete in progress':
                            required_services['deleting'].append(service)
                        elif service.status == 'create failed':
                            required_services['failed'].append(service)
                        elif service.status == 'delete failed':
                            required_services['failed'].append(service)
                        else:
                            required_services['unknown'].append(service)
                else:
                    logger.debug("required service is healthy %s" % masked(required_service))

        for s in required_services['deleting']:
            logger.info("waiting for required service %s to be deleted" % str(s))
            cf.wait_for_delete_service(s.name)
            required_services['create'].append(s)
        for s in required_services['failed']:
            logger.warning("required service %s ' in a failed state. Attempting delete..." % s.name)
            cf.delete_service(s.name)

        for s in required_services['wait']:
            cf.wait_for_create_service(s)
        for s in required_services['create']:
            logger.debug("creating service:\n%s" + masked(s))
            cf.create_service(s)


if __name__ == '__main__':
    shared_properties = setup(sys.argv)
    # TODO not sure a better way to make this available to calling shell script
    with open('cf_scdf.properties', 'w') as output:
        for k, v in shared_properties.items():
            output.write('%s=%s\n' % (k, v))
    output.close()
