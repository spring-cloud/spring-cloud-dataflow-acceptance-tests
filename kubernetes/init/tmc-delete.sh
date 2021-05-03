#!/usr/bin/env bash

function delete_tmc_cluster() {

  [[ -z "${TMC_API_TOKEN}" ]] && { echo "Environment variable TMC_API_TOKEN must be set"; exit 1; }
  [[ -z "${CLUSTER_NAME}" ]] && { echo "Environment variable CLUSTER_NAME must be set"; exit 1; }

  echo TMC log-in
  tmc login --name just-some-context-id --no-configure
  tmc configure --management-cluster-name aws-hosted --provisioner-name scdf-provisioner

  echo Delete Cluster: $CLUSTER_NAME
  tmc cluster delete $CLUSTER_NAME
  tmc cluster list

  unset $CLUSTER_NAME
}

delete_tmc_cluster
