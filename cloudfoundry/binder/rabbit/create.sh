#!/bin/bash

source ../../common.sh

create_service "rabbit" $RABBIT_SERVICE_NAME $RABBIT_PLAN_NAME

load_file "$PWD/env.properties"
