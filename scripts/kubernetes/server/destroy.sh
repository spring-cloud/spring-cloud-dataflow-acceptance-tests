#!/bin/bash

set -o errexit

kubectl delete rc/scdf
kubectl delete svc/scdf
kubectl delete secret/scdf-secrets
