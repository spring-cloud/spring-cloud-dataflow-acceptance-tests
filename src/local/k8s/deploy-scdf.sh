#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]; then
  SCDIR="."
fi

if [ "$BINDER" == "" ]; then
  export BINDER="rabbit"
fi

if [ "$K8S_DRIVER" == "" ]; then
  K8S_DRIVER=kind
fi
set -e
if [ "$DOCKER_USER" == "" ] || [ "$DOCKER_SERVER" == "" ] || [ "$DOCKER_PASSWORD" == "" ]; then
  echo "DOCKER_SERVER, DOCKER_USER, DOCKER_PASSWORD, DOCKER_EMAIL is required" >&2
  exit 1
fi

kubectl create secret docker-registry registry-key --docker-server=$DOCKER_SERVER --docker-username=$DOCKER_USER --docker-password=$DOCKER_PASSWORD --docker-email=$DOCKER_EMAIL
kubectl patch serviceaccount default -p '{"imagePullSecrets": [{"name": "registry-key"}]}'

if [ "$USE_PRO" == "" ]; then
  USE_PRO=false
fi

if [ "$DATAFLOW_VERSION" == "" ]; then
  DATAFLOW_VERSION=2.10.0-SNAPSHOT
fi

if [ "$SKIPPER_VERSION" == "" ]; then
  SKIPPER_VERSION=2.9.0-SNAPSHOT
fi

if [ "$SCDF_PRO_VERSION" == "" ]; then
  SCDF_PRO_VERSION=1.5.0-SNAPSHOT
fi
LI_PATH=$(realpath $SCDIR)
sh "$LI_PATH/load-image.sh" "busybox" "1"
sh "$LI_PATH/load-image.sh" "bitnami/kubectl" "1.23.6-debian-10-r0"
sh "$LI_PATH/load-image.sh" "mariadb" "10.4.22"
if [ "$BINDER" == "kafka" ]; then
  sh "$LI_PATH/load-image.sh" "confluentinc/cp-kafka" "5.5.2"
  sh "$LI_PATH/load-image.sh" "confluentinc/cp-zookeeper" "5.5.2"
else
  sh "$LI_PATH/load-image.sh" "rabbitmq" "3.6.10"
fi
sh "$LI_PATH/load-image.sh" "springcloud/spring-cloud-dataflow-composed-task-runner" "$DATAFLOW_VERSION" false
sh "$LI_PATH/load-image.sh" "springcloud/spring-cloud-skipper-server" "$SKIPPER_VERSION" true

if [ "$USE_PRO" == "true" ]; then
  sh "$LI_PATH/load-image.sh" "springcloud/scdf-pro-server" "$SCDF_PRO_VERSION" true
else
  sh "$LI_PATH/load-image.sh" "springcloud/spring-cloud-dataflow-server" "$DATAFLOW_VERSION" true
fi

ATDIR=$(pwd)

pushd ../spring-cloud-dataflow  > /dev/null
if [ "$BINDER" == "kafka" ]; then
  # Deploy Kafka
  kubectl create -f src/kubernetes/kafka/
else
  # Deploy Rabbit
  kubectl create -f src/kubernetes/rabbitmq/
fi

kubectl create -f src/kubernetes/mariadb/

if [ "$PROMETHEUS" == "true" ]; then
  echo "Loading Prometheus and Grafana"
  sh "$LI_PATH/load-image.sh" "springcloud/spring-cloud-dataflow-grafana-prometheus" "2.10.0-SNAPSHOT"
  sh "$LI_PATH/load-image.sh" "prom/prometheus" "v2.12.0"
  sh "$LI_PATH/load-image.sh" "micrometermetrics/prometheus-rsocket-proxy" "0.11.0"
  kubectl create -f src/kubernetes/prometheus/prometheus-clusterroles.yaml
  kubectl create -f src/kubernetes/prometheus/prometheus-clusterrolebinding.yaml
  kubectl create -f src/kubernetes/prometheus/prometheus-serviceaccount.yaml
  kubectl create -f src/kubernetes/prometheus-proxy/
  kubectl create -f src/kubernetes/prometheus/prometheus-configmap.yaml
  kubectl create -f src/kubernetes/prometheus/prometheus-deployment.yaml
  kubectl create -f src/kubernetes/prometheus/prometheus-service.yaml
  kubectl create -f src/kubernetes/grafana/
fi

# Deploy Spring Cloud Dataflow
kubectl create -f src/kubernetes/server/server-roles.yaml
kubectl create -f src/kubernetes/server/server-rolebinding.yaml
kubectl create -f src/kubernetes/server/service-account.yaml
kubectl create -f "$ATDIR/src/local/k8s/server-config.yaml"
# Deploy Spring Cloud Skipper
if [ "$BINDER" == "kafka" ]; then
  kubectl create -f "$ATDIR/src/local/k8s/skipper-config-kafka.yaml"
else
  kubectl create -f "$ATDIR/src/local/k8s/skipper-config-rabbit.yaml"
fi
kubectl create -f "$ATDIR/src/local/k8s/skipper-deployment.yaml"
kubectl create -f "$ATDIR/src/local/k8s/skipper-svc.yaml"

# Start DataFlow
kubectl create clusterrolebinding scdftestrole --clusterrole cluster-admin --user=system:serviceaccount:default:scdf-sa

kubectl create -f "$ATDIR/src/local/k8s/server-svc.yaml"
if [ "$USE_PRO" == "true" ]; then
  kubectl create -f "$ATDIR/src/local/k8s/server-deployment-pro.yaml"
else
  kubectl create -f "$ATDIR/src/local/k8s/server-deployment.yaml"
fi

popd > /dev/null
