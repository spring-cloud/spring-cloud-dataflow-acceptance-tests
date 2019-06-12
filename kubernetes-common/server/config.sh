#!/usr/bin/env bash

SERVER_URI=$(kubectl get svc scdf --namespace $KUBERNETES_NAMESPACE | grep scdf | awk '{print $4}')
SERVER_URI="https://$SERVER_URI"
echo "SCDF SERVER URI: $SERVER_URI"
export SERVER_URI
export SPRING_CLOUD_DATAFLOW_FEATURES_SKIPPER_ENABLED=false
export DEPLOY_PAUSE_RETRIES=50
