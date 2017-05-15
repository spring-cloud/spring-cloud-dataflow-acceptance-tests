#!/bin/bash

set -o errexit

kubectl delete rc/kafka
kubectl delete svc/kafka
kubectl delete cm/scdf-config
