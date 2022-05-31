#!/usr/bin/env bash

TARGET=kubectl
if [[ "$1" != "" ]]
then
  TARGET=$1
fi
export PLATFORM_TYPE=kubernetes
sh ./src/local/configure-minikube.sh
sh "./src/local/$TARGET/deploy-scdf.sh"
sh ./src/local/build-app-images.sh
sh "./src/local/$TARGET/forward-scdf.sh"
sleep 2
sh ./src/local/register-apps.sh
