#!/bin/bash

set -o errexit

kubectl delete rc/rabbitmq
kubectl delete svc/rabbitmq
kubectl delete cm/scdf-config
