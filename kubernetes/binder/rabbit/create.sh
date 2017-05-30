#!/bin/bash

set -o errexit

load_file "$PWD/env.properties"

kubectl create -f rabbitmq.yml --namespace $KUBERNETES_NAMESPACE
