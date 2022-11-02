#!/bin/bash

kubectl delete deployments --all $WAIT --namespace="$NS"
kubectl delete statefulsets --all $WAIT --namespace="$NS"
kubectl delete svc --all $WAIT --namespace="$NS"
kubectl delete all --all $WAIT --namespace="$NS"
kubectl delete pods --all $WAIT --namespace="$NS"
kubectl delete secrets --all $WAIT --namespace="$NS"
kubectl delete pvc --all $WAIT --namespace="$NS"
kubectl delete secrets --namespace "$NS" --all
kubectl delete pvc --namespace "$NS" --all

if [ "$K8S_DRIVER" != "tmc" ] && [ "$K8S_DRIVER" != "gke" ] ; then
  echo "stopping port forward"
  kubectl_pid=$(ps aux | grep 'kubectl' | grep 'port\-forward' | awk '{print $2}')
  if [ "$kubectl_pid" != "" ]
  then
    kill $kubectl_pid
  fi
fi
if [ "$NS" != "default" ]; then
  kubectl delete namespace "$NS"
fi
