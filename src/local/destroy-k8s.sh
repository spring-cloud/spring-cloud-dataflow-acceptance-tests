#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]; then
  SCDIR="."
fi
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
  ;;
*)
  minikube delete
  ;;
esac
