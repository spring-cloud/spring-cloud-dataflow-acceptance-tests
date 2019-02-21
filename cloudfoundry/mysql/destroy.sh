#!/bin/bash

source ../common.sh

destroy_service "mysql"

destroy_service "mysql_skipper"
