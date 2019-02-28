#!/usr/bin/env bash

set -o errexit

# ======================================= FUNCTIONS START =======================================

function pks_authenticate_and_target() {
  [ -z "$PKS_CLUSTER_NAME" ] && { echo "Environment variable PKS_CLUSTER_NAME must be set"; exit 1; }
  [ -z "$PKS_ENDPOINT" ] && { echo "Environment variable PKS_ENDPOINT must be set"; exit 1; }
  [ -z "$PKS_USERNAME" ] && { echo "Environment variable PKS_USERNAME must be set"; exit 1; }
  [ -z "$PKS_PASSWORD" ] && { echo "Environment variable PKS_PASSWORD must be set"; exit 1; }

  echo "Using PKS cluster: $PKS_CLUSTER_NAME"

  pks login -a $PKS_ENDPOINT -u $PKS_USERNAME -p $PKS_PASSWORD --skip-ssl-validation
  pks get-credentials $PKS_CLUSTER_NAME
  kubectl config use-context $PKS_CLUSTER_NAME
}

# ======================================= FUNCTIONS END =======================================

if ! command_exists pks; then
  echo "You don't have the 'PKS' installed, please visit https://network.pivotal.io/products/pivotal-container-service to install it first"
  exit 1
fi
if ! command_exists kubectl; then
  echo "You don't have the 'kubectl' command line tool installed, please visit https://kubernetes.io/docs/tasks/tools/install-kubectl to install it first"
  exit 1
fi

echo "Connecting to kubernetes cluster"

pks_authenticate_and_target

if [ -z "$KUBERNETES_NAMESPACE" ]; then
  export KUBERNETES_NAMESPACE='default'
fi
echo "Using namespace $KUBERNETES_NAMESPACE"
