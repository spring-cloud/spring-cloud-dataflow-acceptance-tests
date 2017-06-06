#!/bin/bash

kubectl delete rc/redis --namespace $KUBERNETES_NAMESPACE
kubectl delete svc/redis --namespace $KUBERNETES_NAMESPACE
