#!/bin/bash

kubectl delete rc/scdf --namespace $KUBERNETES_NAMESPACE || true
kubectl delete svc/scdf --namespace $KUBERNETES_NAMESPACE || true
kubectl delete secret/scdf-secrets --namespace $KUBERNETES_NAMESPACE || true
