#!/usr/bin/env bash

[ -z "$JAVA_HOME" ] && { echo "Environment variable JAVA_HOME must be set"; exit 1; }

echo "Setting the environment variables"

load_file "$PWD/env.properties"

echo "Setting retries to $RETRIES and WAIT_TIME to $WAIT_TIME"
