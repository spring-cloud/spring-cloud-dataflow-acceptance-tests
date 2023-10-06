#!/usr/bin/env bash
(return 0 2>/dev/null) && sourced=1 || sourced=0
function check_env() {
  eval ev='$'$1
  if [ "$ev" == "" ]; then
    echo "$1 not defined"
    if ((sourced != 0)); then
      return 1
    else
      exit 1
    fi
  fi
}
function register_app() {
  set +e
  echo "Registering $1 as $2"
  wget -q -O- "http://$DATAFLOW_IP/apps/$1" --post-data="uri=$2"
  RC=$?
  if ((RC > 0)); then
    echo "Error registering $1: $RC"
  fi
}
IGNORE_ERRORS=$1
set -e
check_env NS
check_env DATAFLOW_IP
check_env BROKER
if [ "$BROKER" = "rabbitmq" ]; then
  export BROKERNAME=rabbit
else
  export BROKERNAME=$BROKER
fi
if [ "$BROKERNAME" = "" ]; then
  echo "Error expected BROKERNAME from $BROKER"
  exit 2
fi
# override completely by providing a correct value
case "$2" in
"main")
  STREAM_APPS_VERSION=4.0.0-SNAPSHOT
  ;;
"ga")
  STREAM_APPS_VERSION=2021.1.2
  ;;
"release")
  STREAM_URI="https://dataflow.spring.io/$BROKER-docker-latest"
  ;;
*)
  STREAM_APPS_VERSION="$2"
  echo "Using STREAM_APPS_VERSION: $2"
  ;;
esac
# change to RELEASE to use the latest version or any specific version
if [ "$STREAM_URI" = "" ]; then
  if [ "$STREAM_APPS_VERSION" = "" ]; then
    STREAM_URI="https://dataflow.spring.io/$BROKER-docker-latest"
  else
    if [[ "$STREAM_APPS_VERSION" = *"SNAPSHOT"* ]]; then
      STREAM_APPS_DL_VERSION=$STREAM_APPS_VERSION
      META_DATA="https://repo.spring.io/snapshot/org/springframework/cloud/stream/app/stream-applications-descriptor/${STREAM_APPS_VERSION}/maven-metadata.xml"
      echo "Downloading $META_DATA"
      curl -o maven-metadata.xml -s $META_DATA
      DL_TS=$(xmllint --xpath "/metadata/versioning/snapshot/timestamp/text()" maven-metadata.xml | sed 's/\.//')
      STREAM_APPS_DL_VERSION=$(xmllint --xpath "/metadata/versioning/snapshotVersions/snapshotVersion[extension/text() = 'pom' and updated/text() = '$DL_TS']/value/text()" maven-metadata.xml)
      STREAM_URI="https://repo.spring.io/snapshot/org/springframework/cloud/stream/app/stream-applications-descriptor/${STREAM_APPS_VERSION}/stream-applications-descriptor-${STREAM_APPS_DL_VERSION}.stream-apps-${BROKERNAME}-docker"
    elif [[ "$STREAM_APPS_VERSION" = *"-M"* ]]; then
      STREAM_URI="https://repo.spring.io/milestone/org/springframework/cloud/stream/app/stream-applications-descriptor/${STREAM_APPS_VERSION}/stream-applications-descriptor-${STREAM_APPS_VERSION}.stream-apps-${BROKERNAME}-docker"
    else
      STREAM_URI="https://repo.maven.apache.org/maven2/org/springframework/cloud/stream/app/stream-applications-descriptor/${STREAM_APPS_VERSION}/stream-applications-descriptor-${STREAM_APPS_VERSION}.stream-apps-${BROKERNAME}-docker"
    fi
  fi
fi
echo "Registering $STREAM_URI"
set +e
wget -qO- http://$DATAFLOW_IP/apps --post-data="uri=$STREAM_URI"
if [ "$IGNORE_ERRORS" != "true" ]; then
  RC=$?
  if [ "$RC" != "0" ]; then
    kubectl logs --since=3m --timestamps=true -l app=scdf-server --namespace $NS
    exit $RC
  fi
fi
wget -qO- "http://$DATAFLOW_IP/apps" --post-data="uri=https://dataflow.spring.io/task-docker-latest"
RC=$?
if [ "$IGNORE_ERRORS" != "true" ]; then
  if [ "$RC" != "0" ]; then
    kubectl logs --since=120s --timestamps=true -l app=scdf-server --namespace $NS
    exit $RC
  fi
fi
register_app "task/timestamp/2.0.2" "maven:io.spring:timestamp-task:2.0.2"
register_app "task/timestamp-batch/2.0.2" "maven:io.spring:timestamp-batch-task:2.0.2"
register_app "task/scenario/0.0.1-SNAPSHOT" "maven:io.spring:scenario-task:0.0.1-SNAPSHOT"
register_app "task/batch-remote-partition/0.0.2-SNAPSHOT" "maven:org.springframework.cloud.dataflow.acceptence.tests:batch-remote-partition:0.0.2-SNAPSHOT"
register_app "sink/ver-log/3.0.1" "maven:org.springframework.cloud.stream.app:log-sink-$BROKERNAME:3.0.1"
register_app "sink/ver-log/2.1.5.RELEASE" "maven:org.springframework.cloud.stream.app:log-sink-$BROKERNAME:2.1.5.RELEASE"
register_app "task/task-demo-metrics-prometheus/2.0.1-SNAPSHOT" "maven:io.spring.task:task-demo-metrics-prometheus:2.0.1-SNAPSHOT"
