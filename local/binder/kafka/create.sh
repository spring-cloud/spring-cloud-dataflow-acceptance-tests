#!/usr/bin/env bash

source ../../common.sh

create_kafka_docker_compose_file $PWD
echo "cleaning up any previous kafka docker containers..."
docker_stop "wurstmeister/zookeeper"
docker_stop "wurstmeister/kafka:2.11-0.11.0.3"
echo "done cleaning up"
create "kafka" 9092
run_scripts "$PWD" "config.sh"
