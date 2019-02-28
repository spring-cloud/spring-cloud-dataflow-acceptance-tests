#!/usr/bin/env bash

set -o errexit

kubectl create -f redis.yml --namespace $KUBERNETES_NAMESPACE
