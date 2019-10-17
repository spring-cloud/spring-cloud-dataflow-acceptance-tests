#!/usr/bin/env bash

function distro_files_object_delete() {
  kubectl delete all -l app=rabbitmq --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete all,pvc,secrets -l app=mysql --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete all,cm -l app=skipper --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete all -l app=kafka-broker --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete all,cm -l app=scdf-server --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete role scdf-role --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete rolebinding scdf-rb --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete serviceaccount scdf-sa --namespace $KUBERNETES_NAMESPACE || true
}

if [ -z "$USE_DISTRO_FILES" ]; then
  helm delete scdf --purge || true
else
  distro_files_object_delete
fi

