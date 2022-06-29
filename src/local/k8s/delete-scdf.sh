#!/usr/bin/env bash
kubectl delete all -l app=skipper
kubectl delete all,cm -l app=scdf-server
kubectl delete all -l app=mariadb

if [ "$BINDER" == "" ] || [ "$BINDER" == "rabbit" ]; then
  kubectl delete all -l app=rabbitmq
else
  kubectl delete all -l app=kafka
fi

if [ "$PROMETHEUS" == "true" ]; then
  kubectl delete all -l app=prometheus
  kubectl delete all -l app=grafana
fi

# For some reason these remain behind
kubectl delete persistentvolumeclaims mariadb
kubectl delete role scdf-role
kubectl delete rolebinding scdf-rb
kubectl delete serviceaccount scdf-sa
kubectl delete configmap skipper
kubectl delete clusterrolebinding scdftestrole
kubectl delete clusterrole,clusterrolebinding,sa -l app=prometheus-proxy
kubectl delete clusterrole,clusterrolebinding,sa -l app=prometheus
kubectl delete all,cm,svc,secrets -l app=grafana
kubectl delete secret registry-key
kubectl delete secrets mariadb
