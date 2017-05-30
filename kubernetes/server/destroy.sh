#!/bin/bash

set -o errexit

kubectl delete rc/scdf --namespace $KUBERNETES_NAMESPACE
kubectl delete svc/scdf --namespace $KUBERNETES_NAMESPACE
kubectl delete secret/scdf-secrets --namespace $KUBERNETES_NAMESPACE
