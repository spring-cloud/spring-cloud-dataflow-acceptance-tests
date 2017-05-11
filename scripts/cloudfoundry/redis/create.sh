#!/bin/bash

source ../common.sh

create_service "redis" $REDIS_SERVICE_NAME $REDIS_PLAN_NAME
