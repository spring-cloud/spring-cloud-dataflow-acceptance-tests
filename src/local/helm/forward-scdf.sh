#!/usr/bin/env bash
kubectl rollout status deployment scdf-spring-cloud-dataflow-server
kubectl rollout status deployment scdf-spring-cloud-dataflow-skipper
export SERVICE_PORT=$(kubectl get --namespace default -o jsonpath="{.spec.ports[0].port}" services scdf-spring-cloud-dataflow-server)
kubectl port-forward --namespace default svc/scdf-spring-cloud-dataflow-server "9393:${SERVICE_PORT}" &

