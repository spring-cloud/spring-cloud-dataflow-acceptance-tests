#!/usr/bin/env bash

[ -z "$JAVA_HOME" ] && { echo "Environment variable JAVA_HOME must be set"; exit 1; }

echo "Setting the environment variables"
if [ -z "$DATAFLOW_SERVER_NAME" ]; then
    DATAFLOW_SERVER_NAME="spring-cloud-dataflow-server-local"
fi
load_file "$PWD/env.properties"

echo "Setting retries to $RETRIES and WAIT_TIME to $WAIT_TIME"
