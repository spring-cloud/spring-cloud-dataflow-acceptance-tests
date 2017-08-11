#!/bin/bash

source ../common.sh

cf create-service -c '{"git": { "uri": "https://github.com/ilayaperumalg/config-repo"}}' $CONFIG_SERVER_SERVICE_NAME $CONFIG_SERVER_PLAN_NAME cloud-config-server
