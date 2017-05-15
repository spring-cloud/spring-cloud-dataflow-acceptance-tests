#!/bin/bash

set -o errexit

kubectl create -f rabbitmq.yml
