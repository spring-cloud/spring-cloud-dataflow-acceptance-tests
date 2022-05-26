#!/usr/bin/env bash
minikube image load springcloudstream/scdf-app-restaurant:latest --overwrite=true
echo "loaded springcloudstream/scdf-app-restaurant:latest"
minikube image load springcloudstream/scdf-app-customer:latest --overwrite=true
echo "loaded springcloudstream/scdf-app-customer:latest"
minikube image load springcloudstream/scdf-app-waitron:latest --overwrite=true
echo "loaded springcloudstream/scdf-app-waitron:latest"
