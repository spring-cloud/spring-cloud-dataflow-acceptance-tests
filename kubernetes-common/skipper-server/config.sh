#!/usr/bin/env bash

SKIPPER_SERVER_URI=$(kubectl get svc skipper --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
SKIPPER_SERVER_URI="http://$SKIPPER_SERVER_URI:7577"
echo "SKIPPER SERVER IMAGE: springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION"
echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
export SKIPPER_SERVER_URI
export DEPLOY_PAUSE_RETRIES=50
