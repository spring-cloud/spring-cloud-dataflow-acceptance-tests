#!/usr/bin/env bash

set -o errexit

function wait-tmc-cluster() {
  echo waiting tmc to give kube config
  for i in $(seq 40)
  do
    (tmc cluster auth kubeconfig get $CLUSTER_NAME > /dev/null 2>&1) && break || echo "waiting cluster $i/40 ..."
    sleep 30
  done
  tmc cluster auth kubeconfig get $CLUSTER_NAME > /dev/null
}

function configure_tmc_cli() {

  [[ -z "${TMC_PLATFORM}" ]] && { echo "Environment variable TMC_PLATFORM must be set to either 'linux' or 'darwin'!"; exit 1; }

  if [ -z "$TMC_PLATFORM" ]; then
    # Use darwin for Mac OS.
    export TMC_PLATFORM=linux
  fi

  if [ -z "$TMC_BIN_FOLDER" ]; then
    # For Bamboo you must set TMC_BIN_FOLDER=/home/bamboo/bin/tmc
    export TMC_BIN_FOLDER=/usr/local/bin/
  fi

  echo TMC_PLATFORM: $TMC_PLATFORM
  echo TMC_BIN_FOLDER: $TMC_BIN_FOLDER

  if [ ! -f "$TMC_BIN_FOLDER/tmc" ]; then
    echo Install TMC
    curl -sLO https://tmc-cli.s3-us-west-2.amazonaws.com/tmc/latest/$TMC_PLATFORM/x64/tmc
    chmod +x ./tmc
    cp ./tmc $TMC_BIN_FOLDER
  fi

  echo TMC log-in
  tmc login --name just-some-context-id --no-configure
  tmc configure --management-cluster-name aws-hosted --provisioner-name scdf-provisioner
}

function create_tmc_cluster() {

  [[ -z "${TMC_API_TOKEN}" ]] && { echo "Environment variable TMC_API_TOKEN must be set"; exit 1; }
  [[ -z "${TMC_SSH_KEY}" ]] && { echo "Environment variable TMC_SSH_KEY must be set"; exit 1; }
  [[ -z "${DOCKER_HUB_USERNAME}" ]] && { echo "Environment variable DOCKER_HUB_USERNAME must be set"; exit 1; }
  [[ -z "${DOCKER_HUB_PASSWORD}" ]] && { echo "Environment variable DOCKER_HUB_PASSWORD must be set"; exit 1; }

  if [ -z "$K8S" ]; then
    export K8S=1.19.4-1-amazon2
  fi

  if [ -z "$CLUSTER_NAME" ]; then
    export CLUSTER_NAME=scdf-oss-$(date +%s%M)
  fi

  echo CLUSTER_NAME: $CLUSTER_NAME
  echo K8S: $K8S

  echo -n "" > /tmp/CLUSTER_NAME.txt
  echo $CLUSTER_NAME > /tmp/CLUSTER_NAME.txt

  configure_tmc_cli

  echo Materizlize the dataflow-tmc-template.yaml
  cp ./kubernetes/init/dataflow-tmc-template.yaml /tmp/$CLUSTER_NAME-dataflow-tmc-template.yaml

  if [ "$TMC_PLATFORM" == "darwin" ]; then
    sed -i '' 's/name-changeme/'"$CLUSTER_NAME"'/g' /tmp/$CLUSTER_NAME-dataflow-tmc-template.yaml
    sed -i '' 's/version-changeme/'"$K8S"'/g' /tmp/$CLUSTER_NAME-dataflow-tmc-template.yaml
    sed -i '' 's/sshkey-changeme/'"$TMC_SSH_KEY"'/g' /tmp/$CLUSTER_NAME-dataflow-tmc-template.yaml
  else
    sed -i 's/name-changeme/'"$CLUSTER_NAME"'/g' /tmp/$CLUSTER_NAME-dataflow-tmc-template.yaml
    sed -i 's/version-changeme/'"$K8S"'/g' /tmp/$CLUSTER_NAME-dataflow-tmc-template.yaml
    sed -i 's/sshkey-changeme/'"$TMC_SSH_KEY"'/g' /tmp/$CLUSTER_NAME-dataflow-tmc-template.yaml
  fi

  echo Create cluster: $CLUSTER_NAME
  tmc cluster create -f /tmp/$CLUSTER_NAME-dataflow-tmc-template.yaml

  wait-tmc-cluster

  echo Cluster $CLUSTER_NAME created!

  echo SET KUBECONFIG
  tmc cluster auth kubeconfig get $CLUSTER_NAME > /tmp/kube-$CLUSTER_NAME.config
  export KUBECONFIG=/tmp/kube-$CLUSTER_NAME.config
  chmod og-r $KUBECONFIG

  echo KUBECONFIG: $KUBECONFIG
  cat $KUBECONFIG

  echo CONFIGURE Kube Rolebinders
  kubectl create rolebinding rolebinding-default-privileged-sa-ns_default \
        --namespace default \
        --clusterrole=vmware-system-tmc-psp-privileged \
        --user=system:serviceaccount:default:default

  kubectl create clusterrolebinding default-privileged-cluster-role-binding \
        --clusterrole=vmware-system-tmc-psp-privileged \
        --group=system:authenticated

  kubectl create secret docker-registry scdf-metadata-default \
        --docker-username=$DOCKER_HUB_USERNAME \
        --docker-password=$DOCKER_HUB_PASSWORD
}

create_tmc_cluster

