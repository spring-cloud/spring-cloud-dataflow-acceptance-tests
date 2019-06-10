#!/usr/bin/env bash

source ../../common.sh

load_file "$PWD/env.properties"
if [[ $STREAM_APPS_VERSION == *"latest"* ]]; then
  STREAM_REGISTRATION_RESOURCE=https://dataflow.spring.io/kafka-maven-latest
fi
if [[ $TASKS_VERSION == *"latest"* ]]; then
  TASK_REGISTRATION_RESOURCE=https://dataflow.spring.io/task-maven-latest
fi
APPLICATION_ARGS="$APPLICATION_ARGS  --spring.cloud.dataflow.applicationProperties.stream.spring.cloud.stream.kafka.binder.brokers=$SERVICE_HOST --spring.cloud.dataflow.applicationProperties.stream.spring.cloud.stream.kafka.binder.zkNodes=$SERVICE_HOST"
