#!/usr/bin/env bash
if [ "$K8S_DRIVER" == "" ]; then
  K8S_DRIVER=kind
fi

set -e
if [ "$K8S_DRIVER" != "tmc" ] && [ "$K8S_DRIVER" != "gke" ] ; then
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
    echo "Loading $IMAGE to kind"
    kind load docker-image "$IMAGE" "$IMAGE"
    ;;
  "tce")
    echo "Harbor push will be supported soon"
    ;;
  "gke")
    echo "gcr push will be supported soon"
    ;;
  "tmc")
    echo "not supported in TMC"
    ;;
  *)
    echo "Loading $IMAGE to minikube"
    DOCKER_IDS=$(docker images | grep -F "$1" | grep -F "$2" | awk '{print $3}')
    MK_IDS=$(minikube image ls --format=table | grep -F "$1" | grep -F "$2" | awk '{print $6}')
    for did in $DOCKER_IDS; do
      for mid in $MK_IDS; do
        # Docker id may be shorter than Minikube id.
        if [ "${mid:0:12}" == "${did:0:12}" ]; then
          echo "$IMAGE already uploaded"
          exit 0
        fi
      done
    done
    minikube image load "$IMAGE"
    ;;
  esac
  echo "Loaded:$IMAGE"
fi

