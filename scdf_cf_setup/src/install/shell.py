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

import subprocess
import shlex
import logging

logger = logging.getLogger(__name__)


class Shell:
    def __init__(self, dry_run=False):
        self.dry_run = dry_run

    def exec(self, cmd, capture_output=True):
        args = shlex.split(cmd)
        if self.dry_run:
            logger.info("dry_run: " + cmd)
            proc = subprocess.CompletedProcess(args, 0)
            return proc
        else:
            return subprocess.run(args, capture_output=capture_output)

    @classmethod
    def log_stdout(cls, completed_proc):
        logger.info(cls.stdout_to_s(completed_proc))

    @classmethod
    def stdout_to_s(cls, completed_proc):
        return completed_proc.stdout.decode() if completed_proc.stdout else ""

    @classmethod
    def log_stderr(cls, completed_proc):
        logger.info(completed_proc.stderr.decode() if completed_proc.stdout else "")

    @classmethod
    def log_command(cls, completed_proc, msg=""):
        logger.info(msg + ": " + shlex.join(completed_proc.args))
