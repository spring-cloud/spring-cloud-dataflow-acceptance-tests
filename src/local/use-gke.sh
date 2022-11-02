#!/bin/bash
(return 0 2>/dev/null) && sourced=1 || sourced=0
if [ "$sourced" = "0" ]; then
  echo "This script must be invoked using: source $0 $*"
  exit 1
fi
function usage() {
    echo "Usage $0 <cluster> [namespace]"
}
if [ "$1" = "" ]; then
  usage
  return 1
fi

if [ "$2" != "" ]; then
  export NS="$2"
else
  export NS=default
fi
export K8S_DRIVER=gke
export GKE_CLUSTER="$1"
echo "Connecting to $GKE_CLUSTER..."
export REGION=$(gcloud container clusters list | grep -F "$CLUSTER_NAME" | awk '{print $2}')
if [ "$REGION" = "" ]; then
  echo "Cannot find REGION from $CLUSTER_NAME"
  return 1
fi
print "\rConnecting to $GKE_CLUSTER at $REGION"
export KUBECONFIG=$HOME/.kube/config-gke
gcloud container clusters get-credentials $GKE_CLUSTER --region $REGION
echo "KUBECONFIG set to $KUBECONFIG"
echo "Namespace: $NS"
