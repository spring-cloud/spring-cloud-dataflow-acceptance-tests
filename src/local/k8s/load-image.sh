#!/usr/bin/env bash
if [ "$K8S_DRIVER" == "" ]
then
  K8S_DRIVER=kind
fi
BROKER=rabbitmq
if [ "$BROKER" = "rabbitmq" ]
then
  BROKER_NAME=rabbit
else
  BROKER_NAME=$BROKER
fi
set -e
DONT_PULL=$3
if [[ "$2" == "" ]]
then
  echo "A TAG is required for $1"
  exit 2
fi
IMAGE="$1:$2"
set +e
docker images | grep "$1" | grep "$2" > /dev/null
set -e
EXISTS=$?
if [ "$DONT_PULL" != "true" ]
then
  if [[ "$2" == *"SNAPSHOT"* ]] || [[ "$2" == *"latest"* ]] || [[ "$EXISTS" != 0 ]]
  then
    echo "Pulling:$IMAGE"
    docker pull "$IMAGE"
  else
    echo "Exists:$IMAGE"
  fi
else
  echo "Not pulling:$IMAGE"
fi
set +e
docker images | grep "$1" | grep "$2" > /dev/null
set -e
EXISTS=$?
if [[ "$EXISTS" != 0 ]]
then
  echo "Image not found $IMAGE"
  exit 2
fi
err=$(docker history "$IMAGE")
rc=$?
if [[ $rc -ne 0 ]]
then
  echo "$err"
  exit 1
fi
echo "Loading:$IMAGE"
case "$K8S_DRIVER" in
  "kind")
    kind load docker-image "$IMAGE" "$IMAGE"
  ;;
  "tce")
    # echo "Cannot upload to TCE"
    kind load docker-image "$IMAGE" --name scdf-local
  ;;
  "tmc")
    echo "not supported"
    exit 1
  ;;
  *)
    minikube image load "$IMAGE"
  ;;
esac
echo "Loaded:$IMAGE"
