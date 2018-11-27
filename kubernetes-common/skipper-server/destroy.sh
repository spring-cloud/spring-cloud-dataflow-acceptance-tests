#!/bin/bash

kubectl delete rc/skipper --namespace $KUBERNETES_NAMESPACE || true
kubectl delete svc/skipper --namespace $KUBERNETES_NAMESPACE || true
