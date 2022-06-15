#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]; then
  SCDIR="."
fi
set -e
pushd "$SCDIR/../../../spring-cloud-dataflow-samples/restaurant-stream-apps"
pushd scdf-app-kitchen
./mvnw install spring-boot:build-image -Dspring-boot.build-image.pullPolicy=IfNotPresent -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-kitchen:1.0.0-SNAPSHOT
popd

pushd scdf-app-customer
./mvnw install spring-boot:build-image -Dspring-boot.build-image.pullPolicy=IfNotPresent -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-customer:1.0.0-SNAPSHOT
popd

pushd scdf-app-waitron
./mvnw install spring-boot:build-image -Dspring-boot.build-image.pullPolicy=IfNotPresent -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-waitron:1.0.0-SNAPSHOT
popd
popd
