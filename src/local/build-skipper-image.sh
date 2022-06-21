#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]; then
  SCDIR="."
fi
if [ "$SKIPPER_VERSION" == "" ]; then
  SKIPPER_VERSION=2.9.0-SNAPSHOT
fi

pushd "$SCDIR/../../../spring-cloud-skipper" > /dev/null
./mvnw -o clean install -DskipTests
pushd spring-cloud-skipper-server > /dev/null
../mvnw -o spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION
popd > /dev/null
popd > /dev/null
