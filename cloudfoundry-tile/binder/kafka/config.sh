#!/usr/bin/env bash
[[ -z "$DATAFLOW_TILE_KAFKA_BROKER_ADDRESS" ]] && { echo "$(basename $BASH_SOURCE) DATAFLOW_TILE_KAFKA_BROKER_ADDRESS is required"; exit 1; }
[[ -z "$DATAFLOW_TILE_KAFKA_USERNAME" ]] && { echo "$(basename $BASH_SOURCE) DATAFLOW_TILE_KAFKA_USERNAME is required"; exit 1; }
[[ -z "$DATAFLOW_TILE_KAFKA_PASSWORD" ]] && { echo "$(basename $BASH_SOURCE) DATAFLOW_TILE_KAFKA_PASSWORD is required"; exit 1; }
load_file "$PWD/env.properties"
if [[ $STREAM_APPS_VERSION == *"latest"* ]]; then
  STREAM_REGISTRATION_RESOURCE=https://dataflow.spring.io/kafka-maven-latest
fi
if [[ $TASK_APPS_VERSION == *"latest"* ]]; then
  TASK_REGISTRATION_RESOURCE=https://dataflow.spring.io/task-kafka-latest
fi
