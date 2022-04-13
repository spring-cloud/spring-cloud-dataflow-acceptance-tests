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

if [[ -z "${DATAFLOW_SERVICE_ACCOUNT_NAME}" ]]; then
  # use the default service account as it can be pre-patched, open issue on DH limiting:
  # https://github.com/bitnami/charts/issues/4430
  export DATAFLOW_SERVICE_ACCOUNT_NAME=default

  if [ -n "$USE_DISTRO_FILES" ]; then
    export DATAFLOW_SERVICE_ACCOUNT_NAME=scdf-sa
  fi
fi

echo "Connecting to kubernetes cluster: ${CLUSTER_NAME} in namespace ${KUBERNETES_NAMESPACE}"

kubernetes_authenticate_and_target

