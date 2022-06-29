#!/usr/bin/env bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")

set -e
pushd "$SCDIR/../../../spring-cloud-dataflow-samples/restaurant-stream-apps"  > /dev/null
pushd scdf-app-kitchen  > /dev/null
./mvnw install spring-boot:build-image -DskipTests -Dspring-boot.build-image.pullPolicy=IfNotPresent -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-kitchen:1.0.0-SNAPSHOT
popd > /dev/null
pushd scdf-app-customer > /dev/null
./mvnw install spring-boot:build-image -DskipTests -Dspring-boot.build-image.pullPolicy=IfNotPresent -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-customer:1.0.0-SNAPSHOT
popd > /dev/null
pushd scdf-app-waitron > /dev/null
./mvnw install spring-boot:build-image -DskipTests -Dspring-boot.build-image.pullPolicy=IfNotPresent -Dspring-boot.build-image.imageName=springcloudstream/scdf-app-waitron:1.0.0-SNAPSHOT
popd > /dev/null
