#!/usr/bin/env bash
(return 0 2>/dev/null) && sourced=1 || sourced=0
if [ "$sourced" == "0" ]; then
  echo "This script must be invoked using: source $0 $*"
  exit 1
fi
function usage() {
    echo "Usage $0 <cluster> <region> [namespace]"
}
if [ "$1" == "" ]; then
  usage
  return 2
fi

if [ "$2" == "" ]; then
  usage
  return 2
fi
if [ "$3" != "" ]; then
  export NS="$3"
else
  export NS=default
fi
export K8S_DRIVER=gke
export GKE_CLUSTER="$1"
export REGION="$2"
echo "Connecting to $GKE_CLUSTER at $REGION"
gcloud container clusters get-credentials $GKE_CLUSTER --region $REGION > $HOME/.kube/config
export KUBECONFIG=$HOME/.kube/config
echo "KUBECONFIG set"
echo "Namespace: $NS"
