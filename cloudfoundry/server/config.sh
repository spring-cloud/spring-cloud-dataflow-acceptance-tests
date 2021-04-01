#!/usr/bin/env bash

function config_application() {
  export PLATFORM_SUFFIX=$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN
}

config_application
