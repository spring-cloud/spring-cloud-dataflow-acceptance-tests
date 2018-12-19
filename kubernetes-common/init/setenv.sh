#!/bin/bash

echo "kubernetes-common contains shared files and not intended to be used as its own target"
if [ -z "$DATAFLOW_SERVER_NAME" ]; then
    DATAFLOW_SERVER_NAME="spring-cloud-dataflow-server-kubernetes"
fi

exit 1

