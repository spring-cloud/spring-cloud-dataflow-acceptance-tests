#!/bin/sh
source ../../common.sh

create_kafka_docker_compose_file $PWD

destroy "kafka" 9092
