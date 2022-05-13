import unittest

from cloudfoundry.platform.config.configuration import ConfigurationProperties
from cloudfoundry.platform.registration import AppRegistrations


class MockCloudFoundry:
    def oauth_token(self):
        return 'bearer eyJhbGciOiJSUzI1NiIsImprdSI6I'


class RegistrationTests(unittest.TestCase):
    def test_parse_app_import_entries(self):
        config_props = ConfigurationProperties.from_env_vars(
            {'BINDER': 'rabbit'})
        app_reg = AppRegistrations(cf=MockCloudFoundry(), config_props=config_props, server_uri='http://dataflow-server.apps.somehost.cf-app.com')
        data = 'sink.dataflow-tasklauncher=maven://org.springframework.cloud:spring-cloud-dataflow-tasklauncher-sink-$BINDER:$DATAFLOW_VERSION'
        app_name, app_type, url, version = app_reg.parse_app(data)
        self.assertEqual('sink', app_type)
        self.assertEqual('dataflow-tasklauncher', app_name)
        self.assertEqual('maven://org.springframework.cloud:spring-cloud-dataflow-tasklauncher-sink-rabbit:2.10.0-M1',
                         url)
        self.assertEqual('2.10.0-M1', version)

        data = 'task.scenario=maven://io.spring:scenario-task:0.0.1-SNAPSHOT'
        app_name, app_type, url, version = app_reg.parse_app(data)
        self.assertEqual('task', app_type)
        self.assertEqual('scenario', app_name)
        self.assertEqual('maven://io.spring:scenario-task:0.0.1-SNAPSHOT', url)
        self.assertEqual('0.0.1-SNAPSHOT', version)
