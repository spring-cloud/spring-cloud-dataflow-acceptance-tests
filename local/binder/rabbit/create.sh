#!/bin/bash

source ../../common.sh

create "rabbitmq" 5672
run_scripts "$PWD" "config.sh"
