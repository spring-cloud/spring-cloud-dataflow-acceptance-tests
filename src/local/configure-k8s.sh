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

case "$K8S_DRIVER" in
  "kind")
    echo "Creating kind cluster"
    kind create cluster --image kindest/node:v1.23.6
  ;;
  "tmc")
    if [ "$TMC_CLUSTER" == "" ]
    then
      echo "Cannot find environmental variable TMC_CLUSTER"
      exit 2
    fi
    if [ "$KUBECONFIG" == "" ]
    then
      echo "Please execute source $SCDIR/tmc/set-cluster.sh to establish KUBECONFIG"
      exit 2
    fi
  ;;
  *)
    echo "Creating Minikube cluster with $K8S_DRIVER"
    # K8S_DRIVER=kvm2, docker, vmware, virtualbox, podman, vmwarefusion
    minikube start --cpus=4 --memory=10g --driver=$K8S_DRIVER
  ;;
esac
kubectl get namespaces | grep default
RC=$?
if [ "$RC" != "0" ]
then
  kubectl create -f "$SCDIR/k8s/default-ns.yaml"
fi
if [ "$K8S_DRIVER" == "kind" ]
then
  sh "$SCDIR/k8s/setup-metallb.sh"
else
  if [ "$K8S_DRIVER" != "tmc" ]
  then
    echo "When using Minikube there is no LoadBalancer which means http sink won't work"
  fi
fi