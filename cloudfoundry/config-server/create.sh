#!/bin/bash

source ../common.sh

cf create-service -c '{"git": { "uri": "https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests"}}' $CONFIG_SERVER_SERVICE_NAME $CONFIG_SERVER_PLAN_NAME cloud-config-server
