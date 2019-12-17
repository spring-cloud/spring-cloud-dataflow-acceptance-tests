#!/usr/bin/env bash

function use_helm() {
  # cleanup any failed distro file deployments if needed
  distro_files_object_delete

  helm delete scdf --purge || true

  echo "Waiting for cleanup"
  sleep 60

  HELM_PARAMS="--set server.image=springcloud/$DATAFLOW_SERVER_NAME --set server.version=$DATAFLOW_VERSION \
    --set skipper.version=$SKIPPER_VERSION --set server.service.labels.spring-deployment-id=scdf \
    --set skipper.service.labels.spring-deployment-id=skipper --set skipper.trustCerts=true \
    --set server.trustCerts=true --set skipper.imagePullPolicy=Always --set server.imagePullPolicy=Always"

  if [ "$BINDER" == "kafka" ]; then
    HELM_PARAMS="$HELM_PARAMS --set kafka.enabled=true,rabbitmq.enabled=false"
  fi

  if [ ! -z "$HELM_CHART_VERSION" ]; then
    HELM_PARAMS="$HELM_PARAMS --version $HELM_CHART_VERSION"
  fi

  helm repo update
  helm install --name scdf stable/spring-cloud-data-flow ${HELM_PARAMS} --namespace $KUBERNETES_NAMESPACE
  helm list
}

# functions prefixed with distro_ replicate how in-tree k8s files are deployed
# per the user guide for the base install. changes to the user guide and visa versa
# should be in sync.
function distro_files_install() {
  distro_files_object_delete

  distro_files_clone_repo

  pushd spring-cloud-dataflow > /dev/null

  distro_files_install_binder
  distro_files_install_database
  distro_files_install_rbac
  distro_files_install_skipper
  distro_files_install_scdf

  popd > /dev/null
}

function distro_files_object_delete() {
  kubectl delete all -l app=rabbitmq --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete all,pvc,secrets -l app=mysql --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete all,cm -l app=skipper --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete all -l app=kafka --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete all,cm -l app=scdf-server --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete role scdf-role --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete rolebinding scdf-rb --namespace $KUBERNETES_NAMESPACE || true
  kubectl delete serviceaccount scdf-sa --namespace $KUBERNETES_NAMESPACE || true

  echo "Waiting for cleanup"
  sleep 60
}

function distro_files_install_binder() {
  if [ "$BINDER" == "kafka" ]; then
    kubectl create -f src/kubernetes/kafka/ --namespace $KUBERNETES_NAMESPACE
  else
    kubectl create -f src/kubernetes/rabbitmq/ --namespace $KUBERNETES_NAMESPACE
  fi
}

function distro_files_clone_repo() {
  rm -rf spring-cloud-dataflow
  git clone https://github.com/spring-cloud/spring-cloud-dataflow.git
  pushd spring-cloud-dataflow > /dev/null
  git fetch --all --tags

  REPO_VERSION="origin/master"

  # origin/master, tags/v2.3.0.M1 etc
  if [ -n "$DISTRO_FILES_REPO_VERSION" ]; then
    REPO_VERSION="$DISTRO_FILES_REPO_VERSION"
  fi

  git checkout $REPO_VERSION -b $REPO_VERSION

  popd > /dev/null
}

function distro_files_install_database() {
  kubectl create -f src/kubernetes/mysql/ --namespace $KUBERNETES_NAMESPACE
}

function distro_files_install_rbac() {
  kubectl create -f src/kubernetes/server/server-roles.yaml --namespace $KUBERNETES_NAMESPACE
  kubectl create -f src/kubernetes/server/server-rolebinding.yaml --namespace $KUBERNETES_NAMESPACE
  kubectl create -f src/kubernetes/server/service-account.yaml --namespace $KUBERNETES_NAMESPACE
}

function distro_files_install_skipper() {
  if [ "$BINDER" == "kafka" ]; then
    kubectl create -f src/kubernetes/skipper/skipper-config-kafka.yaml --namespace $KUBERNETES_NAMESPACE
  else
    kubectl create -f src/kubernetes/skipper/skipper-config-rabbit.yaml --namespace $KUBERNETES_NAMESPACE
  fi

  kubectl create -f src/kubernetes/skipper/skipper-deployment.yaml --namespace $KUBERNETES_NAMESPACE
  kubectl create -f src/kubernetes/skipper/skipper-svc.yaml --namespace $KUBERNETES_NAMESPACE
}

function distro_files_install_scdf() {
  kubectl create -f src/kubernetes/server/server-config.yaml --namespace $KUBERNETES_NAMESPACE
  kubectl create -f src/kubernetes/server/server-svc.yaml --namespace $KUBERNETES_NAMESPACE
  kubectl create -f src/kubernetes/server/server-deployment.yaml --namespace $KUBERNETES_NAMESPACE
}

function wait_for_skipper() {
  WAIT_TIME=10

  SKIPPER_SERVER_URI=$(kubectl get ingress --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $2}')
  $(wait_for_200 https://${SKIPPER_SERVER_URI}/api)

  SKIPPER_SERVER_URI=$(kubectl get svc skipper --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
  export SKIPPER_SERVER_URI="https://$SKIPPER_SERVER_URI:7577"

  echo "SKIPPER SERVER IMAGE: springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION"
  echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
}

if [ -z "$DATAFLOW_SERVER_NAME" ]; then
  DATAFLOW_SERVER_NAME="spring-cloud-dataflow-server-kubernetes"
fi

if [ -n "$USE_DISTRO_FILES" ]; then
  distro_files_install
else
  use_helm
fi

wait_for_skipper

