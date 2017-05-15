#!/bin/bash

set -o errexit

kubectl delete rc/mysql
kubectl delete svc/mysql
