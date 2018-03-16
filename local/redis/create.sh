#!/bin/bash

source ../common.sh

echo "cleaning up any previous redis docker containers..."
docker ps -q --filter ancestor="redis" | xargs -r docker stop
echo "done cleaning up."
create "redis" 6379

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.redis.host=$SERVICE_HOST"
