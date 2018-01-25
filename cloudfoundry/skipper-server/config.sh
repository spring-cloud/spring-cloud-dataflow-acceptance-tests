#!/bin/bash

function config_application() {
  SKIPPER_SERVER_URI=$(cf app skipper-server | grep skipper-server- | awk '{print $2}' | sed 's:,::g')
  SKIPPER_SERVER_URI="http://$SKIPPER_SERVER_URI"
  echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
  export SKIPPER_SERVER_URI
}

config_application
