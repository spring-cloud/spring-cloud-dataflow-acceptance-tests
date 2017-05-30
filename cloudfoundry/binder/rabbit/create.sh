#!/bin/bash

source ../../common.sh

create_service "rabbit" $RABBIT_SERVICE_NAME $RABBIT_PLAN_NAME

run_scripts "$PWD" "config.sh"
