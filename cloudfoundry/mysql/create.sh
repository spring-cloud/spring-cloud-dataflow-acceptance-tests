#!/bin/bash

source ../common.sh

create_service "mysql" $MYSQL_SERVICE_NAME $MYSQL_PLAN_NAME

create_service "mysql_skipper" $MYSQL_SERVICE_NAME $MYSQL_PLAN_NAME
