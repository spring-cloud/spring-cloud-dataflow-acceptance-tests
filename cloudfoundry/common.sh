#!/usr/bin/env bash

SCDFAT_RETRY_SLEEP=${SCDFAT_RETRY_SLEEP:-5}
SCDFAT_RETRY_MAX=${SCDFAT_RETRY_MAX:-100}      # set to <0 for no max (infinite)

MSG_COLOR=
ERR_COLOR=
RESET_COLOR=
if [[ "$TERM" == xterm-*color ]]; then
  MSG_COLOR='\033[48;5;2;38;5;0m'   # black on green
  ERR_COLOR='\033[38;5;7;48;5;1m'   # white on red
  RESET_COLOR='\033[0m'
fi

function timestamp() {
  date +"%Y-%m-%d %T"
}

function msg() {
  echo -e "${MSG_COLOR}--- [$(timestamp)] $@${RESET_COLOR}"
}

function err() {
  echo -e "${ERR_COLOR}!!! [$(timestamp)] $@${RESET_COLOR}"
}

die() {
  if [ -n "$@" ]; then
    err $@
  fi
  exit 1
}

function create_service() {
  local service=$1
  local service_type=$2
  local service_plan=$3
  local service_config=$4
  local count=0
  local service_info=
  while [ 1 ]; do
    if ! service_info=$(cf service  "$service" 2>&1); then
      msg "creating service $service [$service_type/$service_plan]"
      if [[ -z $service_config ]]; then
        cf create-service $service_type $service_plan $service
      else
       echo "cf create-service $service_type $service_plan $service -c $service_config"
       cf create-service $service_type $service_plan $service -c "$service_config"
      fi
      continue
    fi
    if [[ $service_info == *"create succeeded"* ]]; then
      break
    fi
    if [[ $service_info == *"create failed"* ]]; then
       die "failed to create service $service"
    fi
    if [[ $service_info == *"delete in progress"* ]]; then
      die "cannot create service $service; it is in the process of being deleted"
    fi
    count=$((count+1))
    if [ $SCDFAT_RETRY_MAX -ge 0 ] && [ $count -gt $SCDFAT_RETRY_MAX ]; then
      die "maximum retries exceeded ($SCDFAT_RETRY_MAX)"
    fi
    if [[ $service_info == *"create in progress"* ]]; then
      msg "waiting for service $service to be created, sleeping ${SCDFAT_RETRY_SLEEP}s ($count of $SCDFAT_RETRY_MAX)"
      sleep $SCDFAT_RETRY_SLEEP
      continue
    fi
    err "unhandled status:"
    die $service_info
  done
}

function destroy_service() {
  local service=$1
  local count=0
  local service_info=
  while [ 1 ]; do
    if ! service_info=$(cf service  "$service" 2>&1); then
      break
    fi
    if [[ $service_info == *"create succeeded"* ]] || [[ $service_info == *"update succeeded"* ]] || [[ $service_info == *"create failed"* ]]; then
      msg "deleting service $service"
      cf delete-service -f $service
      continue
    fi
    if [[ $service_info == *"create in progress"* ]]; then
      die "cannot delete service $service; it is in the process of being created"
    fi
    count=$((count+1))
    if [ $SCDFAT_RETRY_MAX -ge 0 ] && [ $count -gt $SCDFAT_RETRY_MAX ]; then
      die "maximum retries exceeded ($SCDFAT_RETRY_MAX)"
    fi
    if [[ $service_info == *"delete in progress"* ]]; then
      msg "waiting for service $service to be deleted, sleeping ${SCDFAT_RETRY_SLEEP}s ($count of $SCDFAT_RETRY_MAX)"
      sleep $SCDFAT_RETRY_SLEEP
      continue
    fi
    err "unhandled status:"
    echo $service_info
    die
  done
}
