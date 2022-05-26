#!/usr/bin/env bash
eval $(minikube docker-env)
pushd scdf-app-restaurant
  ./mvnw install spring-boot:build-image -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-restaurant
popd

pushd scdf-app-customer
  ./mvnw install spring-boot:build-image -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-customer
popd

pushd scdf-app-waitron
  ./mvnw install spring-boot:build-image -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-waitron
popd
