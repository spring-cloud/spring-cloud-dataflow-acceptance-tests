#!/usr/bin/env bash

SERVER_URI=$(kubectl get ingress --namespace $KUBERNETES_NAMESPACE | grep server | awk '{print $2}')
SERVER_URI="https://$SERVER_URI"

$(wait_for_200 ${SERVER_URI}/about)

echo "SCDF SERVER URI: $SERVER_URI"
export SERVER_URI
