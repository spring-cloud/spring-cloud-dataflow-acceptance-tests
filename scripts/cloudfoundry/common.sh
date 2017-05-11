#!/bin/bash

function create_service() {
  SCDF_SERVICE_NAME=$1
  SERVICE_NAME=$2
  PLAN_NAME=$3
  if ! (cf services | grep "^$SCDF_SERVICE_NAME"); then
    cf cs $SERVICE_NAME $PLAN_NAME $SCDF_SERVICE_NAME
  fi
}

function destroy_service() {
  if (cf services | grep "^$1"); then
    cf ds $1 -f
  fi
}
