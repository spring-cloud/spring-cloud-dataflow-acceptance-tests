#!/usr/bin/env bash
if [ "$K8S_DRIVER" == "" ]; then
  K8S_DRIVER=kind
fi

set -e
DONT_PULL=$3
if [[ "$2" == "" ]]; then
  echo "A TAG is required for $1" >&2
  exit 2
fi
IMAGE="$1:$2"
set +e
docker images | grep -F "$1" | grep -F "$2" >/dev/null
EXISTS=$?
set -e
if [ "$DONT_PULL" != "true" ]; then
  if [[ "$2" == *"SNAPSHOT"* ]] || [[ "$2" == *"latest"* ]] || [[ "$EXISTS" != "0" ]]; then
    echo "Pulling:$IMAGE"
    docker pull "$IMAGE"
  else
    echo "Exists:$IMAGE"
  fi
else
  echo "Not pulling:$IMAGE"
fi
set +e
docker images | grep -F "$1" | grep -F "$2" >/dev/null
EXISTS=$?
set -e
if [[ "$EXISTS" != 0 ]]; then
  echo "Image not found $IMAGE" >&2
  exit 2
fi
err=$(docker history "$IMAGE")
rc=$?
if [[ $rc -ne 0 ]]; then
  echo "$err" >&2
  exit 1
fi
echo "Loading:$IMAGE"
case "$K8S_DRIVER" in
"kind")
  kind load docker-image "$IMAGE" "$IMAGE"
  ;;
"tce")
  kind load docker-image "$IMAGE" --name scdf-local
  ;;
"tmc")
  echo "not supported in TMC"
  ;;
*)
  minikube image load "$IMAGE"
  ;;
esac
echo "Loaded:$IMAGE"
