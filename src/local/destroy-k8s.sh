#!/usr/bin/env bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
sh "$SCDIR/k8s/delete-scdf.sh"
kubectl_pid=$(ps aux | grep 'kubectl' | grep 'port\-forward' | awk '{print $2}')
if [ "$kubectl_pid" != "" ]
then
  kill $kubectl_pid
fi
if [ "$K8S_DRIVER" == "" ]; then
  K8S_DRIVER=kind
fi

case "$K8S_DRIVER" in
"kind")
  kind delete cluster
  ;;
"tmc")
  sh "$SCDIR/tmc/delete-cluster.sh"
  tmc cluster delete "$TMC_CLUSTER"
  ;;
*)
  minikube delete
  ;;
esac
