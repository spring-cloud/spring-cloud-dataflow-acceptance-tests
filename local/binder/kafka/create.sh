#!/bin/bash

source ../../common.sh

create_kafka_docker_compose_file $PWD
echo "cleaning up any previous kafka docker containers..."
docker ps -q --filter ancestor="wurstmeister/zookeeper" | xargs -r docker stop
docker ps -q --filter ancestor="wurstmeister/kafka:0.10.2.1" | xargs -r docker stop
echo "done cleaning up"
create "kafka" 9092
run_scripts "$PWD" "config.sh"
