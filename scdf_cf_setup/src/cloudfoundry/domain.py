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
import re
import src.install.util as util


class JSonEnabled(json.JSONEncoder):
    def __json__(self):
        return self.__dict__.copy()

    def masked(self):
        return util.masked(self.__dict__)

    def __str__(self):
        return json.dumps(self)


class App(JSonEnabled):
    pattern = re.compile('(.+):\s+(.*)')

    @classmethod
    def parse(cls, contents):

        s = {}
        for line in contents.split('\n'):
            line = line.strip()
            match = re.match(cls.pattern, line)
            if match:
                s[match[1].strip()] = match[2].strip()

        return App(name=s.get('name'),
                   route=s.get('routes'))

    def __init__(self, name, route):
        self.name = name
        self.route = route


class Service(JSonEnabled):
    pattern = re.compile('(.+)\:\s+(.*)')

    @classmethod
    def parse(cls, contents):
        s = {}
        for line in contents.split('\n'):
            line = line.strip()
            match = re.match(cls.pattern, line)
            if match:
                s[match[1].strip()] = match[2].strip()

        return Service(name=s.get('name'),
                       service=s.get('service'),
                       plan=s.get('plan'),
                       status=s.get('status'),
                       message=s.get('message'))

    def __init__(self, name, service, plan, status, message):
        self.name = name
        self.service = service
        self.plan = plan
        self.status = status
        self.message = message
