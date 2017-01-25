#!/bin/sh

source ../../common.sh

create_kafka_docker_compose_file
create "kafka" 9092

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.cloud.dataflow.applicationProperties.stream.spring.rabbitmq.host=$DOCKER_SERVER"
