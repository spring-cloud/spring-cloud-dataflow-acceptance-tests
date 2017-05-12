#!/bin/bash

set -o errexit

kubectl delete rc/redis
kubectl delete svc/redis
