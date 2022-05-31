#!/usr/bin/env bash
kubectl rollout status deployment scdf-server
kubectl rollout status deployment skipper
export SERVICE_PORT=9393
export DATAFLOW_IP="http://localhost:$SERVICE_PORT"
kubectl port-forward --namespace default svc/scdf-server "${SERVICE_PORT}:80" &
echo "DATAFLOW_IP=$DATAFLOW_IP"
