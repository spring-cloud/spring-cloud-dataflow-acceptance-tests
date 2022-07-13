#!/usr/bin/env bash
if [ "$NS" == "" ]; then
  echo "NS not defined" >&2
  exit 2
fi
start_time=$(date +%s)
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
LS_DIR=$(realpath $SCDIR)
set -e
if [ "$K8S_DRIVER" == "" ]; then
  K8S_DRIVER=kind
fi
if [ "$BINDER" == "" ]; then
  export BINDER="rabbit"
fi
export PLATFORM_TYPE=kubernetes
if [ "$K8S_DRIVER" == "kind" ]; then
  kubectl apply -f "$LS_DIR/k8s/metallb-configmap.yaml"
fi

sh "$LS_DIR/k8s/deploy-scdf.sh"

if [ "$K8S_DRIVER" != "tmc" ]; then
  sh "$LS_DIR/load-images.sh"
fi
echo "Waiting for mariadb"
kubectl rollout status deployment --namespace "$NS" mariadb
if [ "$BINDER" == "kafka" ]; then
  echo "Waiting for Kafka and Zookeeper"
  kubectl rollout status deployment --namespace "$NS" kafka-zk
  kubectl rollout status sts --namespace "$NS" kafka-broker
else
  echo "Waiting for rabbitmq"
  kubectl rollout status deployment --namespace "$NS" rabbitmq
fi
echo "Waiting for skipper"
kubectl rollout status deployment --namespace "$NS" skipper
echo "Waiting for dataflow"
kubectl rollout status deployment --namespace "$NS" scdf-server

if [ "$K8S_DRIVER" != "tmc" ]; then
  source "$LS_DIR/k8s/forward-scdf.sh"
else
  source "$LS_DIR/k8s/export-dataflow-ip.sh"
fi
sleep 2
sh "$LS_DIR/register-apps.sh"
end_time=$(date +%s)
elapsed=$(( end_time - start_time ))
echo "Complete deployment in $elapsed seconds"
echo "Monitor pods using k9s and kail --ns=default | tee pods.log"
