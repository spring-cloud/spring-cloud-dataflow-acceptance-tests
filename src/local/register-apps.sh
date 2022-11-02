#!/bin/bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
set -e
function dataflow_post() {
  echo "Invoking POST $1 >> $2"
  result=$(curl -s -d "$1" -X POST "$2")
  rc=$?
  # shellcheck disable=SC2086
  if [ "$rc" != "0" ]; then
    echo "$RC : $result"
    echo ""
    exit $RC
  fi
}

if [ "$1" != "" ]; then
  PLATFORM_TYPE=$1
fi
if [ "$PLATFORM_TYPE" = "" ]; then
  PLATFORM_TYPE=kubernetes
fi

if [ "$DATAFLOW_IP" = "" ]; then
  source "$SCDIR/export-dataflow-ip.sh"
fi
case $BROKER in
"" | "kafka")
  export BROKER=kafka
  ;;
"rabbit" | "rabbitmq")
  export BROKER=rabbitmq
  ;;
*)
  echo "BROKER=$BROKER not supported"
esac

if [ "$BROKER" = "rabbitmq" ]; then
  BROKER_NAME=rabbit
else
  BROKER_NAME=$BROKER
fi

STREAM_APPS_VERSION=2021.1.3-SNAPSHOT
# STREAM_APPS_VERSION=2021.1.2
RELEASE_SNAPSHOT=snapshot
# RELEASE_SNAPSHOT=release

if [ "$PLATFORM_TYPE" != "kubernetes" ]; then
  TYPE=maven
else
  TYPE=docker
fi

echo "DATAFLOW_IP=$DATAFLOW_IP"
dataflow_post "uri=https://repo.spring.io/$RELEASE_SNAPSHOT/org/springframework/cloud/stream/app/stream-applications-descriptor/$STREAM_APPS_VERSION/stream-applications-descriptor-$STREAM_APPS_VERSION.stream-apps-$BROKER_NAME-$TYPE" "$DATAFLOW_IP/apps"
if [ "$TYPE" = "docker" ]; then
  dataflow_post "uri=docker:springcloudtask/timestamp-task:2.0.2" "$DATAFLOW_IP/apps/task/timestamp/2.0.2"
  dataflow_post "uri=docker:springcloudtask/timestamp-batch-task:2.0.2" "$DATAFLOW_IP/apps/task/timestamp-batch/2.0.2"
  dataflow_post "uri=docker:springcloudtask/scenario-task:0.0.1-SNAPSHOT" "$DATAFLOW_IP/apps/task/scenario/0.0.1-SNAPSHOT"
  dataflow_post "uri=docker:springcloud/batch-remote-partition:0.0.2-SNAPSHOT" "$DATAFLOW_IP/apps/task/batch-remote-partition/0.0.2-SNAPSHOT"
  dataflow_post "uri=docker:springcloudstream/log-sink-$BROKER_NAME:3.0.1" "$DATAFLOW_IP/apps/sink/ver-log/3.0.1"
  dataflow_post "uri=docker:springcloudstream/log-sink-$BROKER_NAME:2.1.5.RELEASE" "$DATAFLOW_IP/apps/sink/ver-log/2.1.5.RELEASE"
  dataflow_post "uri=docker:springcloudtask/task-demo-metrics-prometheus:0.0.4-SNAPSHOT" "$DATAFLOW_IP/apps/task/task-demo-metrics-prometheus/0.0.4-SNAPSHOT"
else
  dataflow_post "uri=maven:io.spring:timestamp-task:2.0.2" "$DATAFLOW_IP/apps/task/timestamp/2.0.2"
  dataflow_post "uri=maven:io.spring:timestamp-batch-task:2.0.2" "$DATAFLOW_IP/apps/task/timestamp-batch/2.0.2"
  dataflow_post "uri=maven:io.spring:scenario-task:0.0.1-SNAPSHOT" "$DATAFLOW_IP/apps/task/scenario/0.0.1-SNAPSHOT"
  dataflow_post "uri=maven:org.springframework.cloud.dataflow.acceptence.tests:batch-remote-partition:0.0.2-SNAPSHOT" "$DATAFLOW_IP/apps/task/batch-remote-partition/0.0.2-SNAPSHOT"
  dataflow_post "uri=maven:org.springframework.cloud.stream.app:log-sink-$BROKER_NAME:3.0.1" "$DATAFLOW_IP/apps/sink/ver-log/3.0.1"
  dataflow_post "uri=maven:org.springframework.cloud.stream.app:log-sink-$BROKER_NAME:2.1.5.RELEASE" "$DATAFLOW_IP/apps/sink/ver-log/2.1.5.RELEASE"
  dataflow_post "uri=maven:io.spring.task:task-demo-metrics-prometheus:0.0.4-SNAPSHOT" "$DATAFLOW_IP/apps/task/task-demo-metrics-prometheus/0.0.4-SNAPSHOT"
fi
