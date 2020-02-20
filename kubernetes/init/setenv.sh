#!/usr/bin/env bash

set -o errexit

function kubernetes_authenticate_and_target() {
  [[ -z "${KUBECONFIG}" ]] && { echo "Environment variable KUBECONFIG must be set"; exit 1; }
  [[ -z "${CLUSTER_NAME}" ]] && { echo "Environment variable CLUSTER_NAME must be set"; exit 1; }

  kubectl config use-context ${CLUSTER_NAME}
  kubectl config set-context ${CLUSTER_NAME} --namespace ${KUBERNETES_NAMESPACE}
}

if ! command_exists kubectl; then
  echo "You don't have the 'kubectl' command line tool installed, please visit https://kubernetes.io/docs/tasks/tools/install-kubectl to install it first"
  exit 1
fi

if [[ -z "${KUBERNETES_NAMESPACE}" ]]; then
  export KUBERNETES_NAMESPACE='default'
fi

echo "Connecting to kubernetes cluster: ${CLUSTER_NAME} in namespace ${KUBERNETES_NAMESPACE}"

kubernetes_authenticate_and_target
