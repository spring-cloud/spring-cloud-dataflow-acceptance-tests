#!/usr/bin/env bash

source ../../common.sh

create_service "rabbit2" $RABBIT_SERVICE_NAME $RABBIT_PLAN_NAME

run_scripts "$PWD" "config.sh"
