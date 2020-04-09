#!/usr/bin/env bash

function config_application() {
  . ./server-uri.sh
  export PLATFORM_SUFFIX=$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN
}

config_application
