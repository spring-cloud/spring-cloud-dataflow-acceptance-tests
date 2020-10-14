#!/usr/bin/env bash

. ../common.sh

function use_helm() {
  if [ -z "$SKIPPER_VERSION" ]; then
    echo "SKIPPER_VERSION must be defined"
    exit 1
  fi
  if [ -z "$DATAFLOW_VERSION" ]; then
    echo "DATAFLOW_VERSION must be defined"
    exit 1
  fi

  # TODO: once support for SCDF 2.5 is dropped, can clean these values up to only be bitnami and drop
  # legacy stuff

  HELM_PARAMS="--set server.version=$DATAFLOW_VERSION --set skipper.version=$SKIPPER_VERSION \
    --set skipper.service.type=LoadBalancer --set skipper.imagePullPolicy=Always \
    --set server.imagePullPolicy=Always --set deployer.readinessProbe.initialDelaySeconds=0 \
    --set deployer.livenessProbe.initialDelaySeconds=0 --set serviceAccount.name=$DATAFLOW_SERVICE_ACCOUNT_NAME \
    --set server.service.type=LoadBalancer --set server.service.port=80"

  if [ "$BINDER" == "kafka" ]; then
    HELM_PARAMS="$HELM_PARAMS --set kafka.enabled=true,rabbitmq.enabled=false"
  fi

  HELM_CHART_REFERENCE="bitnami/spring-cloud-dataflow"

  if [ -z "$USE_LEGACY_HELM_CHART" ]; then
    HELM_PARAMS="$HELM_PARAMS --set server.image.repository=springcloud/spring-cloud-dataflow-server --set server.image.tag=$DATAFLOW_VERSION \
      --set skipper.image.repository=springcloud/spring-cloud-skipper-server --set skipper.image.tag=$SKIPPER_VERSION \
      --set server.composedTaskRunner.image.repository=springcloud/spring-cloud-dataflow-composed-task-runner \
      --set server.composedTaskRunner.image.tag=$DATAFLOW_VERSION"

    helm repo add bitnami https://charts.bitnami.com/bitnami
  fi

  if [ ! -z "$EXTRA_HELM_PARAMS" ]; then
    HELM_PARAMS="$HELM_PARAMS $EXTRA_HELM_PARAMS"
  fi

  if [ ! -z "$HELM_CHART_VERSION" ]; then
    HELM_PARAMS="$HELM_PARAMS --version $HELM_CHART_VERSION"
  fi

  helm repo update

  if [ ! -z "$USE_LEGACY_HELM_CHART" ]; then
    HELM_CHART_REFERENCE="stable/spring-cloud-data-flow"
	echo "WARN: Using legacy helm chart, bitnami chart should be used as of SCDF 2.6"
  fi

  helm install --name scdf ${HELM_CHART_REFERENCE} ${HELM_PARAMS} --namespace $KUBERNETES_NAMESPACE
  helm list
}

# functions prefixed with distro_ replicate how in-tree k8s files are deployed
# per the user guide for the base install. changes to the user guide and visa versa
# should be in sync.
function distro_files_install() {
  distro_files_clone_repo

  pushd spring-cloud-dataflow

  distro_files_install_binder
  distro_files_install_database
  distro_files_install_rbac
  distro_files_install_skipper
  distro_files_install_scdf

  popd
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
  pushd spring-cloud-dataflow
  git fetch --all --tags

  REPO_VERSION="origin/master"

  # origin/master, tags/v2.3.0.M1 etc
  if [ -n "$DISTRO_FILES_REPO_VERSION" ]; then
    REPO_VERSION="$DISTRO_FILES_REPO_VERSION"
  fi

  echo
  DEBUG "checking out $REPO_VERSION"
  git checkout $REPO_VERSION -b $REPO_VERSION
  popd
}

function extract_sa_name() {
   OLD_SA_NAME=$(cat src/kubernetes/server/server-deployment.yaml | sed -n 's/^.*serviceAccountName: *//p')
}

function update_sa_name() {
  if [[ -z "${OLD_SA_NAME}" ]]; then
     extract_sa_name
  fi
  file=$1
  DEBUG "replacing SA name [$OLD_SA_NAME]  with [$DATAFLOW_SERVICE_ACCOUNT_NAME] in $file"
  cat $file | sed "s/$OLD_SA_NAME/$DATAFLOW_SERVICE_ACCOUNT_NAME/g"
}

function distro_files_install_database() {
  kubectl create -f src/kubernetes/mysql/ --namespace $KUBERNETES_NAMESPACE
}

function distro_files_install_rbac() {
  kubectl create -f src/kubernetes/server/server-roles.yaml --namespace $KUBERNETES_NAMESPACE
  update_sa_name src/kubernetes/server/server-rolebinding.yaml | kubectl create -f - --namespace $KUBERNETES_NAMESPACE
  update_sa_name src/kubernetes/server/service-account.yaml | kubectl create -f - --namespace $KUBERNETES_NAMESPACE
}

function distro_files_install_skipper() {
  if [ "$BINDER" == "kafka" ]; then
    # Remove the configured probe delays
    cat src/kubernetes/skipper/skipper-config-kafka.yaml | sed -e '/readinessProbeDelay:/d' -e '/livenessProbeDelay:/d' \
     | kubectl create -f - --namespace $KUBERNETES_NAMESPACE
    #kubectl create -f src/kubernetes/skipper/skipper-config-kafka.yaml --namespace $KUBERNETES_NAMESPACE
  else
     # Remove the configured probe delays
    cat src/kubernetes/skipper/skipper-config-rabbit.yaml | sed -e '/readinessProbeDelay:/d' -e '/livenessProbeDelay:/d' \
    | kubectl create -f - --namespace $KUBERNETES_NAMESPACE

    #kubectl create -f src/kubernetes/skipper/skipper-config-rabbit.yaml --namespace $KUBERNETES_NAMESPACE
  fi

  update_sa_name src/kubernetes/skipper/skipper-deployment.yaml | kubectl create -f - --namespace $KUBERNETES_NAMESPACE
  kubectl create -f src/kubernetes/skipper/skipper-svc.yaml --namespace $KUBERNETES_NAMESPACE
}

function distro_files_install_scdf() {
  kubectl create -f src/kubernetes/server/server-config.yaml --namespace $KUBERNETES_NAMESPACE
  kubectl create -f src/kubernetes/server/server-svc.yaml --namespace $KUBERNETES_NAMESPACE
  update_sa_name src/kubernetes/server/server-deployment.yaml | kubectl create -f - --namespace $KUBERNETES_NAMESPACE
}


helm_delete
distro_files_object_delete

if [ -n "$USE_DISTRO_FILES" ]; then
  distro_files_install
else
  use_helm
fi

. ./server-uri.sh

