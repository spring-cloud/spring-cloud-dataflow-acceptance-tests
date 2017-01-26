#!/bin/sh

echo "Setting the environment variables"
load_file "$PWD/env.properties"

echo "Setting retries to $RETRIES and WAIT_TIME to $WAIT_TIME"
