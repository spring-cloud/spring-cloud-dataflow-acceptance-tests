#!/usr/bin/env bash
set -e
pushd ../spring-cloud-dataflow-samples/restaurant-stream-apps
  pushd scdf-app-kitchen
    ./mvnw install spring-boot:build-image -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-kitchen
  popd

  pushd scdf-app-customer
    ./mvnw install spring-boot:build-image -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-customer
  popd

  pushd scdf-app-waitron
    ./mvnw install spring-boot:build-image -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-waitron
  popd
popd
