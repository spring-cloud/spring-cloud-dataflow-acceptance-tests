# This is a Python build spec. Not related to other 'setup' modules in the app.
#!/usr/bin/env python

from setuptools import setup, find_packages
import unittest

def setup_test_suite():
    test_loader = unittest.TestLoader()
    test_suite = test_loader.discover('test', pattern='test_*.py')
    return test_suite

setup(name='scdf_cf_setup',
      version='0.0.1',
      test_suite='__main__.setup_test_suite',
      description='A tool to support Spring Cloud Dataflow installations on Cloud Foundry',
      author='David Turanski',
      author_email='dturanski@gmail.com',
      url = 'https://github.com/dturanski/scdf-at',
      packages=find_packages(exclude=[]),
      license='Apache Software License (http://www.apache.org/licenses/LICENSE-2.0)',
      classifiers=[
          # How mature is this project? Common values are
          #   3 - Alpha
          #   4 - Beta
          #   5 - Production/Stable
          'Development Status :: 4 - Beta',

          # Indicate who your project is intended for
          'Intended Audience :: DevOps',
          'Topic :: SCDF :: Libraries :: Python Modules',

          # Pick your license as you wish (should match "license" above)
          'License :: OSI Approved :: Apache Software License',

          # Specify the Python versions you support here. In particular, ensure
          # that you indicate whether you support Python 2, Python 3 or both.
          'Programming Language :: Python :: 3.7'
      ],
      install_requires=[],  # external packages as dependencies
      ext_modules=[],
      tests_require=['mock', 'unittest2'],
      )