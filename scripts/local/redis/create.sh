#!/bin/sh

source ../common.sh

create "redis" 6379

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.redis.host=$DOCKER_SERVER"
