#!/bin/bash

function kubectl_create() {
  kubectl create -f skipper.yml --namespace $KUBERNETES_NAMESPACE
  READY_FOR_TESTS=1
  for i in $( seq 1 "${RETRIES}" ); do
    SKIPPER_SERVER_URI=$(kubectl get svc skipper --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
    [ '<pending>' != $SKIPPER_SERVER_URI ] && READY_FOR_TESTS=0 && break
    echo "Waiting for skipper server external ip. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
    sleep "${WAIT_TIME}"
  done
  SKIPPER_SERVER_URI=$(kubectl get svc skipper --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
  $(netcat_port ${SKIPPER_SERVER_URI} 7577)
  return 0
}

RETRIES=20
WAIT_TIME=15
kubectl_create
run_scripts "$PWD" "config.sh"
