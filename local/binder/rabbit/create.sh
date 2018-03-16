#!/bin/bash

source ../../common.sh

echo "cleaning up any previous rabbit docker containers..."
docker ps -q --filter ancestor="rabbitmq:management" | xargs -r docker stop
echo "done cleaning up."
create "rabbitmq" 5672
run_scripts "$PWD" "config.sh"
