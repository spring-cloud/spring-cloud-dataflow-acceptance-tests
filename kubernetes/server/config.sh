#!/bin/bash

SERVER_URI=$(kubectl get svc scdf --namespace $KUBERNETES_NAMESPACE | grep scdf | awk '{print $3}')
SERVER_URI="http://$SERVER_URI"
echo "SCDF SERVER URI: $SERVER_URI"
export SERVER_URI
export DEPLOY_PAUSE_RETRIES=50
