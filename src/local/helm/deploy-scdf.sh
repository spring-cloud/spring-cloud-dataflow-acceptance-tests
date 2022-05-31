#!/usr/bin/env bash

function load_image() {
  echo "Pulling:$1"
  docker pull $1
  echo "Loading:$1"
  minikube image load $1
  echo "Loaded:$1"
}


load_image "bitnami/kubectl:1.23.6-debian-10-r0"
load_image "bitnami/mariadb:10.6.7-debian-10-r70"
load_image "bitnami/rabbitmq:3.9.16-debian-10-r1"

load_image "bitnami/spring-cloud-skipper:2.8.4-debian-10-r16"
load_image "bitnami/spring-cloud-dataflow-composed-task-runner:2.9.4-debian-10-r16"
load_image "bitnami/spring-cloud-dataflow:2.9.4-debian-10-r14"

helm install -f src/local/helm/values.yaml scdf bitnami/spring-cloud-dataflow
