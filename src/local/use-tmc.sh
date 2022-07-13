#!/usr/bin/env bash
export K8S_DRIVER=tmc
if [ "$1" == "" ]; then
  echo "Expected parameter providing TMC cluster"
  exit 2
fi
(return 0 2>/dev/null) && sourced=1 || sourced=0
if [ "$sourced" == "0" ]; then
  echo "This script must be invoked using: source $0 $*"
  exit 1
fi
export TMC_CLUSTER="$1"
echo "Connecting to $TMC_CLUSTER"
tmc cluster auth kubeconfig get $TMC_CLUSTER > $HOME/.kube/config
export KUBECONFIG=$HOME/.kube/config
echo "KUBECONFIG set"
if [ "$2" == "" ]; then
  export NS=scdf
else
  export NS=$2
fi
echo "Namespace: $NS"
