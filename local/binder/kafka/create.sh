#!/bin/bash

source ../../common.sh

create_kafka_docker_compose_file $PWD
create "kafka" 9092

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.cloud.dataflow.applicationProperties.stream.spring.cloud.stream.kafka.binder.brokers=$SERVICE_HOST --spring.cloud.dataflow.applicationProperties.stream.spring.cloud.stream.kafka.binder.zkNodes=$SERVICE_HOST"
