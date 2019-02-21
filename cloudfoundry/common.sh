#!/bin/bash

MAX_COUNT=250

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
  local count=0
  local service_info=
  while [ 1 ]; do
    if ! service_info=$(cf service  "$service" 2>&1); then
      msg "creating service $service [$service_type/$service_plan]"
      cf create-service $service_type $service_plan $service
      continue
    fi
    if [[ $service_info == *"create succeeded"* ]]; then
      break
    fi
    if [[ $service_info == *"delete in progress"* ]]; then
      die "cannot create service $service; it is in the process of being deleted"
    fi
    count=$((count+1))
    if [ $count -gt $MAX_COUNT ]; then
      die "maximum retries exceeded ($MAX_COUNT)"
    fi
    if [[ $service_info == *"create in progress"* ]]; then
      msg "waiting for service $service to be created ($count)"
      sleep 1
      continue
    fi
    err "unhandled status:"
    echo $service_info
    die
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
    if [[ $service_info == *"create succeeded"* ]]; then
      msg "deleting service $service"
      cf delete-service -f $service
      continue
    fi
    if [[ $service_info == *"create in progress"* ]]; then
      die "cannot delete service $service; it is in the process of being created"
    fi
    count=$((count+1))
    if [ $count -gt $MAX_COUNT ]; then
      die "maximum retries exceeded ($MAX_COUNT)"
    fi
    if [[ $service_info == *"delete in progress"* ]]; then
      msg "waiting for service $service to be deleted ($count)"
      sleep 1
      continue
    fi
    err "unhandled status:"
    echo $service_info
    die
  done
}
