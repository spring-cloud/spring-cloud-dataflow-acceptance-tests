#!/bin/bash

set -o errexit

kubectl delete rc/mysql --namespace $KUBERNETES_NAMESPACE
kubectl delete svc/mysql --namespace $KUBERNETES_NAMESPACE
