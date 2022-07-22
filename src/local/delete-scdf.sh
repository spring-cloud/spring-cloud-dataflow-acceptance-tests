#!/usr/bin/env bash
kubectl delete all --namespace "$NS" -l app=skipper
kubectl delete all,cm --namespace "$NS" -l app=scdf-server
kubectl delete all --namespace "$NS" -l app=mariadb

if [ "$BINDER" == "" ] || [ "$BINDER" == "rabbit" ]; then
  kubectl delete all --namespace "$NS" -l app=rabbitmq
else
  kubectl delete all --namespace "$NS" -l app=kafka
fi

if [ "$PROMETHEUS" == "true" ]; then
  kubectl delete all --namespace "$NS" -l app=prometheus
  kubectl delete all --namespace "$NS" -l app=grafana
fi
kubectl delete secrets --namespace "$NS" --all
kubectl delete all --namespace "$NS" --all
kubectl delete pvc --namespace "$NS" --all
echo "stopping port forward"
kubectl_pid=$(ps aux | grep 'kubectl' | grep 'port\-forward' | awk '{print $2}')
if [ "$kubectl_pid" != "" ]
then
  kill $kubectl_pid
fi
if [ "$NS" != "default" ]; then
  kubectl delete namespace "$NS"
fi
