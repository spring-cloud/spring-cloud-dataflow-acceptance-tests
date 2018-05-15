#!/bin/bash

set -o errexit

# ======================================= FUNCTIONS START =======================================

function gcp_authenticate_and_target() {
  echo "Using cluster settings -> project:$GCLOUD_PROJECT zone:$GCLOUD_COMPUTE_ZONE cluster:$GCLOUD_CONTAINER_CLUSTER"
  [ -z "$GCLOUD_PROJECT" ] && { echo "Environment variable GCLOUD_PROJECT must be set"; exit 1; }
  [ -z "$GCLOUD_COMPUTE_ZONE" ] && { echo "Environment variable GCLOUD_COMPUTE_ZONE must be set"; exit 1; }
  [ -z "$GCLOUD_CONTAINER_CLUSTER" ] && { echo "Environment variable GCLOUD_CONTAINER_CLUSTER must be set"; exit 1; }

  gcloud config set compute/zone $GCLOUD_COMPUTE_ZONE
  gcloud config set container/cluster $GCLOUD_CONTAINER_CLUSTER
  gcloud container clusters get-credentials $GCLOUD_CONTAINER_CLUSTER --zone $GCLOUD_COMPUTE_ZONE --project $GCLOUD_PROJECT
}

# ======================================= FUNCTIONS END =======================================

if ! command_exists gcloud; then
  echo "You don't have the 'Google Cloud SDK' installed, please visit https://cloud.google.com/sdk/downloads to download it first"
  exit 1
fi
if ! command_exists kubectl; then
  echo "You don't have the 'kubectl' command line tool installed, please visit https://kubernetes.io/docs/tasks/tools/install-kubectl to install it first"
  exit 1
fi

echo "Connecting to kubernetes cluster"

gcp_authenticate_and_target

if [ -z "$KUBERNETES_NAMESPACE" ]; then
  export KUBERNETES_NAMESPACE='default'
fi
if [ -z "$SKIPPER_SERVER_IMAGE" ]; then
  export SKIPPER_SERVER_IMAGE=springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION
fi
echo "Using namespace $KUBERNETES_NAMESPACE"
