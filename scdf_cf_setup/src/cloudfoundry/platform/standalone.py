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

import cloudfoundry.platform.manifest.skipper as skipper_manifest
import cloudfoundry.platform.manifest.dataflow as dataflow_manifest

from install.shell import Shell
from install.util import Poller, wait_for_200

logger = logging.getLogger(__name__)


def setup(cf, installation, do_not_download, shell=Shell()):
    """
    :param cf:
    :param installation:
    :param do_not_download:
    :param shell:
    :return:
    """
    poller = Poller(installation.config_props.deploy_wait_sec, installation.config_props.max_retries)

    if do_not_download:
        logger.info("skipping download server of jars")
    else:
        logger.info("downloading jars")
        download_server_jars(installation.config_props, shell)

    skipper_uri = None
    if installation.dataflow_config.streams_enabled:
        logger.debug("deploying skipper server")
        deploy(cf=cf, manifest_path='skipper_manifest.yml',
               create_manifest=skipper_manifest.create_manifest, application_name='skipper-server',
               installation=installation, params={})
        skipper_app = cf.app('skipper-server')
        # TODO: Try https
        skipper_uri = 'http://%s/api' % skipper_app.route
        logger.debug("waiting for skipper api %s to be live" % skipper_uri)
        if not wait_for_200(poller, skipper_uri):
            raise RuntimeError("skipper server deployment failed")

    logger.debug("getting dataflow server url")
    logger.debug("waiting for dataflow server to start")
    deploy(cf=cf, manifest_path='dataflow_manifest.yml', application_name='dataflow-server',
           create_manifest=dataflow_manifest.create_manifest, installation=installation,
           params={'skipper_uri': skipper_uri})

    dataflow_app = cf.app('dataflow-server')
    dataflow_uri = "https://" + dataflow_app.route
    if not wait_for_200(poller, dataflow_uri):
        raise RuntimeError("dataflow server deployment failed")

    runtime_properties=installation.deployer_config.as_env().copy()
    runtime_properties.update({
        'SPRING_CLOUD_DATAFLOW_CLIENT_SERVER_URI' : dataflow_uri
    })
    return runtime_properties


def clean(cf, config):
    pass


def deploy(cf, application_name, manifest_path, create_manifest, installation, params={}):
    manifest = open(manifest_path, 'w')
    try:
        mf = create_manifest(installation, application_name=application_name, params=params)
        manifest.write(mf)
        manifest.close()
        cf.push('-f ' + manifest_path)
    finally:
        manifest.close()


def download_server_jars(config_props, shell):
    skipper_url = 'https://repo.spring.io/snapshot/org/springframework/cloud/spring-cloud-skipper-server/%s/spring-cloud-skipper-server-%s.jar' \
                  % (config_props.skipper_version, config_props.skipper_version)
    download_maven_jar(skipper_url, config_props.skipper_jar_path, shell)

    dataflow_url = 'https://repo.spring.io/snapshot/org/springframework/cloud/spring-cloud-dataflow-server/%s/spring-cloud-dataflow-server-%s.jar' \
                   % (config_props.dataflow_version, config_props.dataflow_version)
    download_maven_jar(dataflow_url, config_props.dataflow_jar_path, shell)


def download_maven_jar(url, destination, shell):
    logger.info("downloading jar %s to %s" % (url, destination))
    from os.path import exists
    from pathlib import Path
    path = Path(destination)

    if not exists(path.parent):
        logger.info("creating directory %s" % path.parent)
        os.mkdir(path.parent)

    if exists(destination):
        logger.debug("deleting existing file %s" % destination)
        os.remove(destination)

    cmd = 'wget %s -q -O %s' % (url, destination)
    try:
        proc = shell.exec(cmd, capture_output=False)
        if proc.returncode:
            raise RuntimeError('FATAL: Unable to download maven artifact %s to %s' % (url, destination))
    except BaseException as e:
        logger.error(e)
        raise e
