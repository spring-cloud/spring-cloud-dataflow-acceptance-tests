#!/bin/bash

kubectl delete rc/kafka --namespace $KUBERNETES_NAMESPACE
kubectl delete svc/kafka --namespace $KUBERNETES_NAMESPACE
kubectl delete cm/scdf-config --namespace $KUBERNETES_NAMESPACE
