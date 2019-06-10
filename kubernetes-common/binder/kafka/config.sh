#!/usr/bin/env bash

set -o errexit

load_file "$PWD/env.properties"
if [[ $STREAM_APPS_VERSION == *"latest"* ]]; then
  STREAM_REGISTRATION_RESOURCE=https://dataflow.spring.io/kafka-docker-latest
fi
if [[ $TASKS_VERSION == *"latest"* ]]; then
  TASK_REGISTRATION_RESOURCE=https://dataflow.spring.io/task-docker-latest
fi
