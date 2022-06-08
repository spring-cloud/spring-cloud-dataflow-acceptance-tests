#!/usr/bin/env bash
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

function load_image() {
  DONT_PULL=$2
  if [ "$DONT_PULL" != "true" ]
  then
    echo "Pulling:$1"
    docker pull "$1"
  fi
  err=$(docker history "$1")
  rc=$?
  if [[ $rc -ne 0 ]]
  then
    echo "$err"
    exit 1
  fi
  echo "Loading:$1"
  if [ "$MK_DRIVER" == "kind" ]
  then
    kind load docker-image "$1" "$1"
  else
    minikube image load "$1"
  fi
  echo "Loaded:$1"
}

load_image "library/busybox:1"
load_image "bitnami/kubectl:1.23.6-debian-10-r0" false
load_image "mariadb:10.4.22" false
load_image "rabbitmq:3.6.10" false
load_image "springcloud/spring-cloud-dataflow-composed-task-runner:$DATAFLOW_VERSION" false
load_image "springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION" true

if [ "$USE_PRO" == "true" ]
then
  load_image "springcloud/scdf-pro-server:$SCDF_PRO_VERSION" true
else
  load_image "springcloud/spring-cloud-dataflow-server:$DATAFLOW_VERSION" true
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
kubectl create -f "$ATDIR/src/local/kubectl/server-config.yaml"
# Deploy Spring Cloud Skipper
kubectl create -f "$ATDIR/src/local/kubectl/skipper-config.yaml"
kubectl create -f "$ATDIR/src/local/kubectl/skipper-deployment.yaml"
kubectl create -f "$ATDIR/src/local/kubectl/skipper-svc.yaml"

# Start DataFlow
kubectl create clusterrolebinding scdftestrole --clusterrole cluster-admin --user=system:serviceaccount:default:scdf-sa

kubectl create -f "$ATDIR/src/local/kubectl/server-svc.yaml"
if [ "$USE_PRO" == "true" ]
then
  kubectl create -f "$ATDIR/src/local/kubectl/server-deployment-pro.yaml"
else
  kubectl create -f "$ATDIR/src/local/kubectl/server-deployment.yaml"
fi

popd
