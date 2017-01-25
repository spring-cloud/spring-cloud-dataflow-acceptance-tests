#!/bin/bash

if ! (cf services | grep "^redis"); then
  cf cs $REDIS_SERVICE_NAME $REDIS_PLAN_NAME redis
fi
