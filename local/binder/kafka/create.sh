#!/bin/bash

source ../../common.sh

create_kafka_docker_compose_file $PWD
create "kafka" 9092
run_scripts "$PWD" "config.sh"
