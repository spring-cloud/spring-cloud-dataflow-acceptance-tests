#!/usr/bin/env bash

function timestamp() {
  date +"%Y-%m-%d %T"
}

function msg() {
  echo -en "\033[38;5;0m"       # black foreground
  echo -en '\033[48;5;2m'       # green backround
  echo -n "--- [$(timestamp)] $@"
  echo -en '\033[0m'            # reset
  echo
}

function err() {
  echo -en "\033[38;5;7m"       # white foreground
  echo -en '\033[48;5;1m'       # red background
  echo -n "!!! [$(timestamp)] $@"
  echo -en '\033[0m'            # reset
  echo
}

die() {
  err $@
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
    if [[ $service_info == *"create in progress"* ]]; then
      msg "waiting for service $service to be created ($count)"
    else
      err "unhandled status:"
      echo $service_info
    fi
    sleep 1
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
    if [[ $service_info == *"delete in progress"* ]]; then
      msg "waiting for service $service to be deleted ($count)"
    else
      err "unhandled status:"
      echo $service_info
    fi
    sleep 1
  done
}

# vim: et sw=2 sts=2
