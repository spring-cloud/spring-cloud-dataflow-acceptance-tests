#!/bin/bash

kubectl delete rc/mysql --namespace $KUBERNETES_NAMESPACE || true
kubectl delete svc/mysql --namespace $KUBERNETES_NAMESPACE || true
