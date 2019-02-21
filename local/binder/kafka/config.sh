#!/usr/bin/env bash

source ../../common.sh

load_file "$PWD/env.properties"

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.cloud.dataflow.applicationProperties.stream.spring.cloud.stream.kafka.binder.brokers=$SERVICE_HOST --spring.cloud.dataflow.applicationProperties.stream.spring.cloud.stream.kafka.binder.zkNodes=$SERVICE_HOST"
