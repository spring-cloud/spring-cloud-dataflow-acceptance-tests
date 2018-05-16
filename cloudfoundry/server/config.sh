#!/bin/bash

function config_application() {
  SERVER_URI=$(cf apps | grep dataflow-server- | awk '{print $6}' | sed 's:,::g')
  SERVER_URI="http://$SERVER_URI"
  echo "SCDF SERVER URI: $SERVER_URI"
  export SERVER_URI
  export PLATFORM_SUFFIX=$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN
}

config_application
