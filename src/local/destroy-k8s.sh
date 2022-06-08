#!/usr/bin/env bash
kubectl_pid=$(ps aux | grep 'kubectl' | grep 'port\-forward' | awk '{print $2}')
if [ "$kubectl_pid" != "" ]
then
  kill $kubectl_pid
fi
if [ "$MK_DRIVER" == "kind" ]
then
  kind delete cluster
else
  minikube delete
fi
