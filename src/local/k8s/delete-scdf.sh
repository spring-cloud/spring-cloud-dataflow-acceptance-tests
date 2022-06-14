#!/usr/bin/env bash
kubectl delete all -l app=skipper
kubectl delete all,cm -l app=scdf-server
kubectl delete all -l app=mariadb
kubectl delete all -l app=rabbitmq
kubectl delete role scdf-role
kubectl delete rolebinding scdf-rb
kubectl delete serviceaccount scdf-sa
kubectl delete configmap skipper
kubectl delete clusterrolebinding scdftestrole
