#!/usr/bin/env bash
set -e
if [ "$MK_DRIVER" == "" ]
then
  MK_DRIVER=kind
fi
if [ "$MK_DRIVER" == "kind" ]
then
  kind create cluster --image kindest/node:v1.23.6
else
  # MK_DRIVER=kvm2, docker, vmware, virtualbox, podman, vmwarefusion
  minikube start --cpus=4 --memory=10g --driver=$MK_DRIVER
fi
