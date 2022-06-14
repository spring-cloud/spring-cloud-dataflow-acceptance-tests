#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]
then
  SCDIR="."
fi

BROKER=rabbitmq
if [ "$BROKER" = "rabbitmq" ]
then
  BROKER_NAME=rabbit
else
  BROKER_NAME=$BROKER
fi
# Sample Stream Apps
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/scdf-app-kitchen" "1.0.0-SNAPSHOT" true
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/scdf-app-customer" "1.0.0-SNAPSHOT" true
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/scdf-app-waitron" "1.0.0-SNAPSHOT" true

# Stream Apps
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/log-sink-$BROKER_NAME" "3.2.1"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/http-source-$BROKER_NAME" "3.2.1"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/transform-processor-$BROKER_NAME" "3.2.1"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/splitter-processor-$BROKER_NAME" "3.2.1"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/router-sink-$BROKER_NAME" "3.2.1"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/analytics-sink-$BROKER_NAME" "3.2.1"
sh "$SCDIR/k8s/load-image.sh" "springcloudstream/time-source-$BROKER_NAME" "3.2.1"

# Task Apps
sh "$SCDIR/k8s/load-image.sh" "springcloudtask/timestamp-task" "2.0.1"
sh "$SCDIR/k8s/load-image.sh" "springcloudtask/timestamp-batch-task" "2.0.1"
