#!/bin/bash

set -o errexit

kubectl delete rc/rabbitmq --namespace $KUBERNETES_NAMESPACE
kubectl delete svc/rabbitmq --namespace $KUBERNETES_NAMESPACE
kubectl delete cm/scdf-config --namespace $KUBERNETES_NAMESPACE
