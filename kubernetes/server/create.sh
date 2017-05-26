#!/bin/bash

function kubectl_create() {
  kubectl create -f scdf.yml
  READY_FOR_TESTS=1
  for i in $( seq 1 "${RETRIES}" ); do
    SERVER_URI=$(kubectl get svc scdf | grep scdf | awk '{print $3}')
    [ '<pending>' != $SERVER_URI ] && READY_FOR_TESTS=0 && break
    echo "Waiting for server external ip. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
    sleep "${WAIT_TIME}"
  done
  SERVER_URI=$(kubectl get svc scdf | grep scdf | awk '{print $3}')
  $(netcat_port ${SERVER_URI} 80)
  return 0
}

RETRIES=12
WAIT_TIME=15
kubectl_create
SERVER_URI=$(kubectl get svc scdf | grep scdf | awk '{print $3}')
SERVER_URI="http://$SERVER_URI"
echo "SCDF SERVER URI: $SERVER_URI"
export SERVER_URI
export DEPLOY_PAUSE_RETRIES=50
