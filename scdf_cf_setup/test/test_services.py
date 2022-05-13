import unittest
from cloudfoundry.platform.config.service import CloudFoundryServicesConfig, ServiceConfig


class ServicesTestCase(unittest.TestCase):
    def test_services_from_env(self):
        services = CloudFoundryServicesConfig.from_env_vars({
            'CF_SERVICE_SQL_SERVICE': '{"sql":{"name":"mysql","service":"p.mysql","plan":"db-small"}}'
        })
        self.assertEqual(services["sql"], ServiceConfig(name="mysql", service="p.mysql", plan="db-small"))


if __name__ == '__main__':
    unittest.main()
