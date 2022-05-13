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
import json
from cloudfoundry.platform.config.environment import EnvironmentAware

logger = logging.getLogger(__name__)


class CloudFoundryServicesConfig(EnvironmentAware):
    """
    """
    prefix = 'CF_SERVICE_'

    @classmethod
    def from_env_vars(cls, env=os.environ):
        services = cls.env_vars(env, cls.prefix)
        services = {k: json.loads(services[k]) for k in services.keys()}
        print(services)
        cf_services = {}
        for service in services.values():
            if len(service.keys()) > 1:
                raise ValueError("expecting a singleton map %s", str(service))
            for service_type in service.keys():
                cf_services[service_type] = ServiceConfig(name=service[service_type].get('name'),
                                                          plan=service[service_type].get('plan'),
                                                          service=service[service_type].get('service'))
        return cf_services if cf_services else cls.defaults()

    @classmethod
    def defaults(cls):
        return {'rabbit': ServiceConfig.rabbit_default(),
                'sql': ServiceConfig.sql_default(),
                'scheduler': ServiceConfig.scheduler_default(),
                'config': ServiceConfig.config_default(),
                'dataflow': ServiceConfig.dataflow_default()
                }


class ServiceConfig(EnvironmentAware):

    def __init__(self, name, service, plan, config=None):
        self.name = name
        self.service = service
        self.plan = plan
        self.config = config
        self.validate()

    def __eq__(self, other):
        if isinstance(other, ServiceConfig):
            return self.name == other.name and self.service and other.service and \
                   self.plan == other.plan and self.config == other.config

    @classmethod
    def of_service(cls, service):
        """

        Args:
            service: A parsed CF service

        Returns:

        """
        return ServiceConfig(name=service.name, service=service.service, plan=service.plan)

    @classmethod
    def rabbit_default(cls):
        return ServiceConfig(name="rabbit", service="p.rabbitmq", plan="single-node")

    @classmethod
    def scheduler_default(cls):
        return ServiceConfig(name="ci-scheduler", service="scheduler-for-pcf", plan="standard")

    @classmethod
    def dataflow_default(cls):
        return ServiceConfig(name="dataflow", service="p-dataflow", plan="standard")

    @classmethod
    def config_default(cls):
        return ServiceConfig(name="config-server", service="p.config-server", plan="standard")

    @classmethod
    def sql_default(cls):
        return ServiceConfig(name="mysql", service="p.mysql", plan="standard")

    def validate(self):
        if not self.name:
            raise ValueError("'name' is required")
        if not self.service:
            raise ValueError("'service' is required")
        if not self.plan:
            raise ValueError("'plan' is required")

        if self.config:
            try:
                json.loads(self.config)
            except BaseException:
                raise ValueError("config is not valid json:" + self.config)
