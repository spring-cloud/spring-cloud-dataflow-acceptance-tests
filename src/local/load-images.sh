#!/usr/bin/env bash
set -e
BROKER=rabbitmq
if [ "$BROKER" = "rabbitmq" ]
then
  BROKER_NAME=rabbit
else
  BROKER_NAME=$BROKER
fi

function load_image() {
  DONT_PULL=$2
  if [ "$DONT_PULL" != "true" ]
  then
    echo "Pulling:$1"
    docker pull "$1"
  fi
  err=$(docker history "$1")
  rc=$?
  if [[ $rc -ne 0 ]]
  then
    echo "$err"
    exit 1
  fi
  echo "Loading:$1"
  if [ "$MK_DRIVER" == "kind" ]
  then
    kind load docker-image "$1" "$1"
  else
    minikube image load "$1"
  fi
  echo "Loaded:$1"
}

# Sample Stream Apps
load_image "springcloudstream/scdf-app-kitchen:1.0.0-SNAPSHOT" true
load_image "springcloudstream/scdf-app-customer:1.0.0-SNAPSHOT" true
load_image "springcloudstream/scdf-app-waitron:1.0.0-SNAPSHOT" true

# Stream Apps
load_image "springcloudstream/log-sink-$BROKER_NAME:3.2.1"
load_image "springcloudstream/http-source-$BROKER_NAME:3.2.1"
load_image "springcloudstream/transform-processor-$BROKER_NAME:3.2.1"
load_image "springcloudstream/splitter-processor-$BROKER_NAME:3.2.1"
load_image "springcloudstream/router-sink-$BROKER_NAME:3.2.1"
load_image "springcloudstream/analytics-sink-$BROKER_NAME:3.2.1"
load_image "springcloudstream/time-source-$BROKER_NAME:3.2.1"

# Task Apps
load_image "springcloudtask/timestamp-task:2.0.1"
load_image "springcloudtask/timestamp-batch-task:2.0.1"

