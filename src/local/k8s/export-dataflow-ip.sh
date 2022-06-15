#!/usr/bin/env bash
EXTERNAL_IP=$(kubectl get services scdf-server | grep -F "scdf-server" | awk '{ print $4 }')
LB_IP=$(kubectl get svc/scdf-server -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "LB_IP=$LB_IP"
echo "EXTERNAL_IP=$EXTERNAL_IP"
if [ "$EXTERNAL_IP" == "<pending>" ]; then
  EXTERNAL_IP=$LB_IP
fi
export DATAFLOW_IP=http://$EXTERNAL_IP:9393
export PLATFORM_TYPE=kubernetes
