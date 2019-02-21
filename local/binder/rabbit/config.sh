#!/usr/bin/env bash

source ../../common.sh

load_file "$PWD/env.properties"

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.cloud.dataflow.applicationProperties.stream.spring.rabbitmq.host=$SERVICE_HOST"
