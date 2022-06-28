#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]; then
  SCDIR="."
fi
if [ "$BINDER" == "" ]; then
  export BINDER=rabbit
else
  export BINDER=kafka
fi

if [ "$BINDER" == "kafka" ]; then
  BROKER=kafka
else
  BROKER=rabbitmq
fi

if [ "$BROKER" = "rabbitmq" ]; then
  BROKER_NAME=rabbit
else
  BROKER_NAME=$BROKER
fi
STREAM_APPS_VERSION="3.2.2-SNAPSHOT"

if [ "$BROKER" == "rabbitmq" ]; then
  # Sample Stream Apps
  echo "Loading Sample Stream Apps images"
  sh "$SCDIR/k8s/load-image.sh" "springcloudstream/scdf-app-kitchen" "1.0.0-SNAPSHOT" true
  sh "$SCDIR/k8s/load-image.sh" "springcloudstream/scdf-app-customer" "1.0.0-SNAPSHOT" true
  sh "$SCDIR/k8s/load-image.sh" "springcloudstream/scdf-app-waitron" "1.0.0-SNAPSHOT" true
fi

# Stream Apps
echo "Loading Stream Apps images"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/log-sink-$BROKER_NAME" "$STREAM_APPS_VERSION"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/http-source-$BROKER_NAME" "$STREAM_APPS_VERSION"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/transform-processor-$BROKER_NAME" "$STREAM_APPS_VERSION"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/splitter-processor-$BROKER_NAME" "$STREAM_APPS_VERSION"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/router-sink-$BROKER_NAME" "$STREAM_APPS_VERSION"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/analytics-sink-$BROKER_NAME" "$STREAM_APPS_VERSION"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/time-source-$BROKER_NAME" "$STREAM_APPS_VERSION"

# Task Apps
echo "Loading Task Apps images"
sh "$SCDIR/k8s/load-image.sh" "springcloudtask/timestamp-task" "2.0.1"
sh "$SCDIR/k8s/load-image.sh" "springcloudtask/timestamp-batch-task" "2.0.1"
