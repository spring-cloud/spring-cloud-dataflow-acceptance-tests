#!/usr/bin/env bash
set -e
TARGET=kubectl
if [[ "$1" != "" ]]
then
  TARGET=$1
fi
export PLATFORM_TYPE=kubernetes
sh ./src/local/configure-k8s.sh
sh "./src/local/$TARGET/deploy-scdf.sh"
sh ./src/local/load-images.sh
sh "./src/local/$TARGET/forward-scdf.sh"
sleep 2
sh ./src/local/register-apps.sh
echo "Monitor pods using k9s and kail --ns=default | tee pods.log"
