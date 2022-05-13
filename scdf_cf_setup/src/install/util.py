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
import shutil
import time
import traceback
from os.path import exists

import requests
import json
from urllib.parse import urlparse, urlunparse

from install.shell import Shell

logger = logging.getLogger(__name__)


def get_traceback(e):
    lines = traceback.format_exception(type(e), e, e.__traceback__)
    return ''.join(lines)


class Poller:
    def __init__(self, wait_sec, max_retries):
        self.wait_sec = wait_sec
        self.max_retries = max_retries

    def wait_for(self, success_condition=lambda x: True, args=[],
                 failure_condition=lambda x: False,
                 wait_message="waiting for condition to be satisfied",
                 success_message="condition satisfied",
                 fail_message="FAILED: condition not satisfied"):
        tries = 0

        predicate = success_condition(*args)
        while not predicate and tries < self.max_retries:
            time.sleep(self.wait_sec)
            tries = tries + 1
            logger.info("%2d/%2d %s" % (tries, self.max_retries, wait_message))
            predicate = success_condition(*args)
            if failure_condition(*args):
                break
        if predicate:
            logger.info(success_message)
        else:
            logger.error(fail_message)
        return predicate


def wait_for_200(poller, url):
    return poller.wait_for(success_condition=lambda url: requests.get(url).status_code == 200,
                           args=[url],
                           success_message=url + " is up!",
                           fail_message=url + " is down")


def masked(obj):
    return json.dumps(__masked__(obj), indent=4)


def __mask_url__(parts):
    masked_query = ""
    if parts.query:
        items = parts.query.split('&')
        i = 0
        for item in items:
            k, v = item.split('=')
            masked_query = masked_query + "%s=%s" % (k, mask(k, v))
            i = i + 1
            if i < len(items):
                masked_query = masked_query + '&'

    new_parts = (parts.scheme, parts.netloc, parts.path, parts.params, masked_query, parts.fragment)
    return urlunparse(new_parts)


def __masked__(obj):
    if type(obj) is str:
        parts = urlparse(obj)
        return __mask_url__(parts) if parts.scheme else obj
    if hasattr(obj, '__dict__') or type(obj) == dict:
        the_dict = obj.__dict__ if hasattr(obj, '__dict__') else obj
        entries = the_dict.copy()
        for k, v in entries.items():
            if hasattr(v, '__dict__') or type(v) == dict:
                entries[k] = __masked__(v)
            else:
                entries[k] = mask(k, __masked__(v))
        return entries
    return obj


def mask(k, v):
    secret_words = ['password', 'secret', 'username', 'user', 'credentials']
    for secret in secret_words:
        if secret in k.lower():
            return "*" * 8 if v else None
    return v


def setup_certs(cert_host, shell=Shell()):
    import_certs = './import_uaa_certs.sh'
    logger.debug("importing the cert_host certificate for %s to a JDK trust-store" % cert_host)
    proc = shell.exec("%s %s" % (import_certs, cert_host), capture_output=False)
    if proc.returncode > 0:
        raise RuntimeError("%s failed")

    java_home = os.getenv('JAVA_HOME')
    if not java_home:
        raise ValueError('JAVA_HOME is not set')
    # The cacerts location is different for Java 8 and 11.
    # Java 1.8
    jre_cacerts = "%s/jre/lib/security/cacerts" % java_home
    if not exists(jre_cacerts):
        logger.info("%s does not exist" % jre_cacerts)
        # Java 11
        jre_cacerts = "%s/lib/security/cacerts" % java_home
        logger.info("trying %s" % jre_cacerts)
    if not exists(jre_cacerts):
        raise RuntimeError("%s does not exist" % jre_cacerts)
    shutil.copyfile(jre_cacerts, 'mycacerts')
    proc = shell.exec(
        '%s/bin/keytool -import -alias myNewCertificate -file %s.cer -noprompt -keystore mycacerts -storepass changeit'
        % (java_home, cert_host), capture_output=False)
    if proc.returncode > 0:
        raise RuntimeError("Unable to create keystore ' %s" % shell.stdout_to_s(proc))
