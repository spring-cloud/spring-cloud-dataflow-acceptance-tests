#!/usr/bin/env bash
if [ "$K8S_DRIVER" == "" ]; then
  K8S_DRIVER=kind
fi
if [ "$USE_PRO" == "true" ]; then
  EXTERNAL_IP=$(kubectl get services scdf-spring-cloud-dataflow-server | grep -F "scdf" | grep -F "server" | awk '{ print $4 }')
  LB_IP=$(kubectl get svc/scdf-spring-cloud-dataflow-server -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
else
  EXTERNAL_IP=$(kubectl get services scdf-server | grep -F "scdf-server" | awk '{ print $4 }')
  LB_IP=$(kubectl get svc/scdf-server -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
fi
echo "LB_IP=$LB_IP"
echo "EXTERNAL_IP=$EXTERNAL_IP"
if [ "$EXTERNAL_IP" == "<pending>" ]; then
  EXTERNAL_IP=$LB_IP
fi
export DATAFLOW_IP=http://$EXTERNAL_IP:9393
export PLATFORM_TYPE=kubernetes
