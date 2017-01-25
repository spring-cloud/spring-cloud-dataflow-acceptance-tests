#!/bin/sh

if ! (cf services | grep "^rabbit"); then
  cf cs $RABBIT_SERVICE_NAME $RABBIT_PLAN_NAME rabbit
fi
