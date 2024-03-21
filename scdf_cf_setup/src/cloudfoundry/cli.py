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

import json
import logging
import re
from install.shell import Shell
from cloudfoundry.domain import Service, App
from install.util import Poller, masked

logger = logging.getLogger(__name__)

'''
Basically a cf cli screen scraper
'''


class CloudFoundry:
    initialized = False

    @classmethod
    def connect(cls, deployer_config, config_props, shell=Shell()):
        logger.debug("connection config:\n%s" % deployer_config.masked())
        cf = CloudFoundry(deployer_config, config_props, shell)

        if not CloudFoundry.initialized:
            logger.debug("logging in to CF - api: %s org: %s space: %s" % (
                deployer_config.api_endpoint, deployer_config.org, deployer_config.space))
            proc = cf.login()
            if proc.returncode:
                logger.error("CF login failed: " + Shell.stdout_to_s(proc))
                cf.logout()
                raise RuntimeError(
                    "cf login failed for some reason. Verify the username/password and that org %s and space %s exist"
                    % (deployer_config.org, deployer_config.space))
            logger.info("\n" + json.dumps(cf.current_target()))
            CloudFoundry.initialized = True
        else:
            logger.debug("Already logged in")
        return cf

    def __init__(self, deployer_config, config_props, shell):
        if not deployer_config:
            raise ValueError("'deployer_config' is required")
        if not config_props:
            raise ValueError("'config_props' is required")
        if not shell:
            raise ValueError("'shell' is required")
        self.poller = Poller(config_props.deploy_wait_sec, config_props.max_retries)
        self.config_props = config_props
        self.deployer_config = deployer_config

        self.shell = shell
        try:
            self.shell.exec('cf --version')
        except Exception:
            raise RuntimeError('cf cli is not installed')

        if shell.dry_run:
            CloudFoundry.initialized = True
            return

        target = self.current_target()

        if target and not target.get('api endpoint') == deployer_config.api_endpoint:
            raise RuntimeError("Already logged in to %s" % str(target.get('api endpoint')))
        # Might be logged in with no space and org
        if target and target.get('api endpoint') == deployer_config.api_endpoint and not (
                target.get('org') == deployer_config.org and target.get('space') == deployer_config.space):
            logger.info(
                "targeting configured environment: org = %s space = %s" % (deployer_config.org, deployer_config.space))
            proc = self.target(org=deployer_config.org)
            if proc.returncode:
                raise RuntimeError("Unable to target org %s" % deployer_config.org)
            proc = self.target(space=deployer_config.space)
            if proc.returncode:
                self.create_space(deployer_config.space)
            target = self.current_target()

        if target and target.get('api endpoint') == deployer_config.api_endpoint and \
                target.get('org') == deployer_config.org and target.get('space') == deployer_config.space:
            CloudFoundry.initialized = True

    def current_target(self):
        proc = self.shell.exec("cf target")
        contents = self.shell.stdout_to_s(proc)
        logger.debug(contents)
        target = {}
        for line in contents.split('\n'):
            if line and ':' in line:
                key = line[0:line.index(':')].strip()
                value = line[line.index(':') + 1:].strip()
                target[key] = value
        logger.debug("current target:\n%s" % json.dumps(target, indent=4))
        return target

    def target(self, org=None, space=None):
        cmd = "cf target"
        if org is not None:
            cmd = cmd + " -o %s" % (org)
        if space is not None:
            cmd = cmd + " -s %s" % (space)
        return self.shell.exec(cmd)

    def push(self, args):
        cmd = 'cf push %s' % args
        proc = self.shell.exec(cmd, capture_output=False)
        if proc.returncode:
            logger.error(self.shell.log_stdout(proc))
            raise RuntimeError('cf push failed: %s' % str(proc.args))
        return proc

    def is_logged_in(self):
        proc = self.shell.exec("cf target")
        return proc.returncode == 0

    def logout(self):
        proc = self.shell.exec("cf logout")
        if proc.returncode == 0:
            CloudFoundry.initialized = False
        return proc

    def login(self):
        skip_ssl = ""
        if self.deployer_config.skip_ssl_validation:
            skip_ssl = "--skip-ssl-validation"

        cmd = "cf login -a %s -o %s -u %s -p %s %s" % \
              (self.deployer_config.api_endpoint,
               self.deployer_config.org,
               self.deployer_config.username,
               self.deployer_config.password,
               skip_ssl)
        return self.shell.exec(cmd)

    def create_service(self, service_config):
        logger.info("creating service " + masked(service_config))

        # Looks like pretty clean code, but having to pass this mess on the command line? WTF
        config = "-c '%s'" % json.dumps(service_config.config) if service_config.config else ""

        proc = self.shell.exec("cf create-service %s %s %s %s" % (service_config.service, service_config.plan,
                                                                  service_config.name, config))
        self.shell.log_stdout(proc)
        if self.shell.dry_run:
            return proc

        if proc.returncode:
            logger.error(self.shell.stdout_to_s(proc))
            return proc

        else:
            self.wait_for_create_service(service_config)
        return proc

    def wait_for_create_service(self, service_config):
        if not self.poller.wait_for(
                success_condition=lambda: self.service(service_config.name).status == 'create succeeded',
                failure_condition=lambda: self.service(service_config.name).status == 'create failed',
                wait_message="waiting for service %s status 'create succeeded'" % service_config.name):
            raise RuntimeError("FATAL: unable to create service %s" % service_config)
        else:
            logger.info("created service %s" % service_config.name)

    def delete_service(self, service_name):
        logger.info("deleting service %s" % service_name)

        proc = self.shell.exec("cf delete-service -f %s" % service_name)
        self.shell.log_stdout(proc)
        if self.shell.dry_run:
            return proc
        if proc.returncode:
            logger.error(self.shell.stdout_to_s(proc))
            return proc
        else:
            self.wait_for_delete_service(service_name)
        return proc

    def wait_for_delete_service(self, service_name):
        def fail():
            service = self.service(service_name)
            return service and service.status == 'delete failed'

        if not self.poller.wait_for(success_condition=lambda: self.service(service_name) is None,
                                    failure_condition=fail,
                                    wait_message="waiting for %s to be deleted" % service_name):
            raise RuntimeError("FATAL: %s " % str(self.service(service_name)))
        else:
            logger.info("deleted service %s" % service_name)

    def service_key(self, service_name, key_name='scdf_cf_setup'):
        logger.info("getting service key %s for service %s" % (key_name, service_name))
        proc = self.shell.exec("cf service-key %s %s" % (service_name, key_name))
        msg = self.shell.stdout_to_s(proc)
        if proc.returncode:
            logger.error(msg)
            return None
        service_key_json = re.sub("Getting key.+\n", "", msg)
        return json.loads(service_key_json)

    def create_service_key(self, service_name, key_name):
        if not self.service_key(service_name, key_name):
            logger.info("creating service key %s for service %s" % (key_name, service_name))
            proc = self.shell.exec("cf create-service-key %s %s" % (service_name, key_name))
            if proc.returncode:
                logger.error(self.shell.stdout_to_s(proc))
                raise RuntimeError("FATAL: Failed to create service key %s %s" % (service_name, key_name))
        else:
            logger.info("service key %s %s already exists" % (service_name, key_name))
        return self.service_key(service_name, key_name)

    def delete_service_key(self, service_name, key_name):
        if self.service_key(service_name, key_name):
            logger.info("deleting service key %s for service %s" % (key_name, service_name))
            proc = self.shell.exec("cf delete-service-key -f %s %s" % (service_name, key_name))
            if proc.returncode:
                logger.error(self.shell.stdout_to_s(proc))
            else:
                logger.info("service-key %s %s deleted" % (service_name, key_name))
            return proc
        else:
            logger.info("service key %s %s does not exist" % (service_name, key_name))
            return None

    def apps(self):
        appnames = []
        proc = self.shell.exec("cf apps")
        contents = self.shell.stdout_to_s(proc)
        i = 0
        for line in contents.split("\n"):
            if i > 3 and line:
                appnames.append(line.split(' ')[0])
            i = i + 1
        return appnames

    def app(self, app_name):
        proc = self.shell.exec("cf app %s" % app_name)
        msg = self.shell.stdout_to_s(proc)
        if proc.returncode:
            logger.error(msg)
            return None
        return App.parse(msg)

    def delete_app(self, app_name):
        proc = self.shell.exec("cf delete -f %s" % app_name)
        msg = self.shell.stdout_to_s(proc)
        if proc.returncode:
            logger.error("Failed to delete app %s [%s]" % (app_name, msg))

    def delete_orphaned_routes(self):
        proc = self.shell.exec("cf delete-orphaned-routes -f")
        msg = self.shell.stdout_to_s(proc)
        if proc.returncode:
            logger.error("Failed to delete orphaned routes %s" % msg)

    def delete_apps(self, apps=None):
        apps = apps if apps else self.apps()
        for app in apps:
            self.delete_app(app)
        self.delete_orphaned_routes()

    def service(self, service_name):
        proc = self.shell.exec("cf service " + service_name)
        if proc.returncode != 0:
            logger.debug("service %s does not exist, or there is some other issue." % service_name)
            return None

        return Service.parse(self.shell.stdout_to_s(proc))

    def services(self):
        logger.debug("getting services")
        proc = self.shell.exec("cf services")
        contents = self.shell.stdout_to_s(proc)
        services = []
        parse_line = False
        for line in contents.split('\n'):
            # Brittle to scrape the text output directly, just grab the name and call `cf service` for each.
            # See self.service().
            if line.strip():
                if line.startswith('name'):
                    parse_line = True

                elif parse_line:
                    row = line.split(' ')
                    services.append(self.service(row[0]))

        logger.debug("existing services:\n" + json.dumps(services, indent=4))
        return services

    def oauth_token(self):
        logger.debug("getting oauth-token")
        proc = self.shell.exec("cf oauth-token")
        contents = self.shell.stdout_to_s(proc)
        if proc.returncode != 0:
            logger.error("failed to get oauth-token: %s" % contents)
            return None
        return contents.rstrip('\n')

    def create_space(self, space):
        logger.info("creating space %s" % space)
        proc = self.shell.exec("cf create-space %s" % space)
        if proc.returncode:
            raise RuntimeError("Unable to create space %s" % space)
