#!/usr/bin/env bash

source ../../common.sh

echo "cleaning up any previous rabbit docker containers..."
docker_stop "rabbitmq:management"
echo "done cleaning up."
create "rabbitmq" 5672
run_scripts "$PWD" "config.sh"
