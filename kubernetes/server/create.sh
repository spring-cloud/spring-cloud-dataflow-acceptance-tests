#!/usr/bin/env bash

SERVER_URI=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep server | awk '{print $4}')
export SERVER_URI="http://$SERVER_URI"

$(wait_for_200 ${SERVER_URI}/about)

echo "SCDF SERVER URI: $SERVER_URI"
