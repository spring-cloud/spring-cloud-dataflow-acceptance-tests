#!/bin/bash

source ../../common.sh

create "rabbitmq" 5672

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.cloud.dataflow.applicationProperties.stream.spring.rabbitmq.host=$SERVICE_HOST"
