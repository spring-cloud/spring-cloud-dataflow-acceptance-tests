#!/bin/bash

source ../common.sh

create "redis" 6379

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.redis.host=$SERVICE_HOST"
