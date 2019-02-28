#!/usr/bin/env bash

kubectl delete rc/redis --namespace $KUBERNETES_NAMESPACE || true
kubectl delete svc/redis --namespace $KUBERNETES_NAMESPACE || true
