#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]
then
  SCDIR="."
fi
set -e
if [ "$K8S_DRIVER" == "" ]
then
  K8S_DRIVER=kind
fi
export PLATFORM_TYPE=kubernetes
if [ "$K8S_DRIVER" == "kind" ]
then
  kubectl apply -f "$SCDIR/k8s/metallb-configmap.yaml"
fi
sh "$SCDIR/k8s/deploy-scdf.sh"
sh "$SCDIR/load-images.sh"
echo "Waiting for mariadb"
kubectl rollout status deployment mariadb
echo "Waiting for rabbitmq"
kubectl rollout status deployment rabbitmq
echo "Waiting for skipper"
kubectl rollout status deployment skipper
echo "Waiting for dataflow"
kubectl rollout status deployment scdf-server

source "$SCDIR/k8s/export-dataflow-ip.sh"
if [ "$K8S_DRIVER" != "tmc" ]
then
  source "$SCDIR/k8s/forward-scdf.sh"
fi
sleep 2
sh "$SCDIR/register-apps.sh"
echo "Monitor pods using k9s and kail --ns=default | tee pods.log"
