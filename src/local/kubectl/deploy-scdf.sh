#!/usr/bin/env bash

function load_image() {
  echo "Pulling:$1"
  docker pull $1
  echo "Loading:$1"
  minikube image load $1
  echo "Loaded:$1"
}
load_image "bitnami/kubectl:1.23.6-debian-10-r0"
load_image "mariadb:10.4.22"
load_image "rabbitmq:3.6.10"
load_image "springcloud/spring-cloud-dataflow-composed-task-runner:2.10.0-SNAPSHOT"
load_image "springcloud/spring-cloud-skipper-server:2.9.0-SNAPSHOT"
load_image "springcloud/spring-cloud-dataflow-server:2.10.0-SNAPSHOT"
ATDIR=$(pwd)

pushd ../spring-cloud-dataflow
# Deploy Rabbit
kubectl create -f src/kubernetes/rabbitmq/
# Deploy Rabbit
kubectl create -f src/kubernetes/mariadb/

# Deploy Spring Cloud Dataflow
kubectl create -f src/kubernetes/server/server-roles.yaml
kubectl create -f src/kubernetes/server/server-rolebinding.yaml
kubectl create -f src/kubernetes/server/service-account.yaml
kubectl create -f "$ATDIR/src/local/kubectl/server-config.yaml"
# Deploy Spring Cloud Skipper
kubectl create -f "$ATDIR/src/local/kubectl/skipper-config-rabbit.yaml"
kubectl create -f src/kubernetes/skipper/skipper-deployment.yaml
kubectl create -f src/kubernetes/skipper/skipper-svc.yaml

# Start DataFlow
kubectl create clusterrolebinding scdftestrole --clusterrole cluster-admin --user=system:serviceaccount:default:scdf-sa
echo "Waiting for mariadb"
kubectl rollout status deployment mariadb
echo "Waiting for rabbitmq"
kubectl rollout status deployment rabbitmq

kubectl create -f src/kubernetes/server/server-svc.yaml
kubectl create -f src/kubernetes/server/server-deployment.yaml

popd
