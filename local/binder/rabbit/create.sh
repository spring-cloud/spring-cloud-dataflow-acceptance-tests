#!/bin/bash

source ../../common.sh

load_file "$PWD/env.properties"
echo "### REG: $REGISTRATION_RESOURCE"
create "rabbitmq" 5672

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.cloud.dataflow.applicationProperties.stream.spring.rabbitmq.host=$SERVICE_HOST"
