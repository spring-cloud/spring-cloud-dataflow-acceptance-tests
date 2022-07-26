#!/usr/bin/env bash
(return 0 2>/dev/null) && sourced=1 || sourced=0
if [ "$sourced" == "0" ]; then
  echo "This script must be invoked using: source $0 $*"
  exit 1
fi
if [ "$NS" == "" ]; then
  echo "NS not defined" >&2
  return 2
fi
if [ "$K8S_DRIVER" == "" ]; then
  K8S_DRIVER=kind
fi
if [ "$USE_PRO" == "true" ]; then
  EXTERNAL_IP=$(kubectl get services --namespace "$NS" scdf-spring-cloud-dataflow-server | grep -F "scdf" | grep -F "server" | awk '{ print $4 }')
  LB_IP=$(kubectl get --namespace "$NS" svc/scdf-spring-cloud-dataflow-server -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
else
  EXTERNAL_IP=$(kubectl get --namespace "$NS" services scdf-server | grep -F "scdf-server" | awk '{ print $4 }')
  LB_IP=$(kubectl get  --namespace "$NS" svc/scdf-server -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
fi
echo "LB_IP=$LB_IP"
echo "EXTERNAL_IP=$EXTERNAL_IP"
if [ "$EXTERNAL_IP" == "<pending>" ]; then
  EXTERNAL_IP=$LB_IP
fi
export DATAFLOW_IP=http://$EXTERNAL_IP:9393
export PLATFORM_TYPE=kubernetes