#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]
then
  SCDIR="."
fi

if [ "$K8S_DRIVER" == "" ]
then
  K8S_DRIVER=kind
fi
set -e
if [ "$DOCKER_USER" == "" ] || [ "$DOCKER_SERVER" == "" ] || [ "$DOCKER_PASSWORD" == "" ]
then
  echo "DOCKER_SERVER, DOCKER_USER, DOCKER_PASSWORD, DOCKER_EMAIL is required"
  exit 1
fi

kubectl create secret docker-registry regcred --docker-server=$DOCKER_SERVER --docker-username=$DOCKER_USER --docker-password=$DOCKER_PASSWORD --docker-email=$DOCKER_EMAIL

if [ "$USE_PRO" == "" ]
then
  USE_PRO=false
fi

if [ "$DATAFLOW_VERSION" == "" ]
then
  DATAFLOW_VERSION=2.10.0-SNAPSHOT
fi

if [ "$SKIPPER_VERSION" == "" ]
then
  SKIPPER_VERSION=2.9.0-SNAPSHOT
fi

if [ "$SCDF_PRO_VERSION" == "" ]
then
  SCDF_PRO_VERSION=1.5.0-SNAPSHOT
fi

sh "$SCDIR/load-image.sh" "busybox" "1"
sh "$SCDIR/load-image.sh" "bitnami/kubectl" "1.23.6-debian-10-r0"
sh "$SCDIR/load-image.sh" "mariadb" "10.4.22"
sh "$SCDIR/load-image.sh" "rabbitmq" "3.6.10"
sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-dataflow-composed-task-runner" "$DATAFLOW_VERSION" false
sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-skipper-server" "$SKIPPER_VERSION" true

if [ "$USE_PRO" == "true" ]
then
  sh "$SCDIR/load-image.sh" "springcloud/scdf-pro-server" "$SCDF_PRO_VERSION" true
else
  sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-dataflow-server" "$DATAFLOW_VERSION" true
fi


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
kubectl create -f "$ATDIR/src/local/k8s/server-config.yaml"
# Deploy Spring Cloud Skipper
kubectl create -f "$ATDIR/src/local/k8s/skipper-config.yaml"
kubectl create -f "$ATDIR/src/local/k8s/skipper-deployment.yaml"
kubectl create -f "$ATDIR/src/local/k8s/skipper-svc.yaml"

# Start DataFlow
kubectl create clusterrolebinding scdftestrole --clusterrole cluster-admin --user=system:serviceaccount:default:scdf-sa

kubectl create -f "$ATDIR/src/local/k8s/server-svc.yaml"
if [ "$USE_PRO" == "true" ]
then
  kubectl create -f "$ATDIR/src/local/k8s/server-deployment-pro.yaml"
else
  kubectl create -f "$ATDIR/src/local/k8s/server-deployment.yaml"
fi

popd