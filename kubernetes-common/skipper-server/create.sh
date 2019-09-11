#!/usr/bin/env bash

helm delete scdf --purge || true

# sleep a few to give things a chance to fully terminate
sleep 60

if [ -z "$DATAFLOW_SERVER_NAME" ]; then
  DATAFLOW_SERVER_NAME="spring-cloud-dataflow-server-kubernetes"
fi

HELM_PARAMS="--set server.image=springcloud/$DATAFLOW_SERVER_NAME --set server.version=$DATAFLOW_VERSION \
--set skipper.version=$SKIPPER_VERSION --set server.service.labels.spring-deployment-id=scdf \
--set skipper.service.labels.spring-deployment-id=skipper --set skipper.trustCerts=true \
--set server.trustCerts=true"

if [ "$BINDER" == "kafka" ]; then
  HELM_PARAMS="$HELM_PARAMS --set kafka.enabled=true,rabbitmq.enabled=false"
fi

helm repo update
helm install --name scdf stable/spring-cloud-data-flow ${HELM_PARAMS} --namespace $KUBERNETES_NAMESPACE

WAIT_TIME=10
SKIPPER_SERVER_URI=$(kubectl get ingress --namespace $KUBERNETES_NAMESPACE | grep data-flow-skipper | awk '{print $2}')
$(wait_for_200 https://${SKIPPER_SERVER_URI}/api)

SKIPPER_SERVER_URI=$(kubectl get svc skipper --namespace $KUBERNETES_NAMESPACE | grep data-flow-skipper | awk '{print $4}')
SKIPPER_SERVER_URI="https://$SKIPPER_SERVER_URI:7577"
echo "SKIPPER SERVER IMAGE: springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION"
echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
export SKIPPER_SERVER_URI
