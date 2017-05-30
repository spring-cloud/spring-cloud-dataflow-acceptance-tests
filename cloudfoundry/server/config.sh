#!/bin/bash

function config_application() {
  SERVER_URI=$(cf app scdf-server | grep urls | awk '{print $2}' | sed 's:,::g')
  SERVER_URI="http://$SERVER_URI"
  echo "SCDF SERVER URI: $SERVER_URI"
  export SERVER_URI
  export PLATFORM_SUFFIX=$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN
}

config_application
