#!/bin/bash

set -o errexit

echo "kubectl create -f src/etc/kubernetes/mysql-controller.yml"
echo "kubectl create -f src/etc/kubernetes/mysql-service.yml"
