#!/usr/bin/env bash
if [ "$SKIPPER_VERSION" == "" ]
then
  SKIPPER_VERSION=2.9.0-SNAPSHOT
fi

pushd ../spring-cloud-skipper
  ./mvnw -o clean install -DskipTests
  pushd spring-cloud-skipper-server
    ../mvnw -o spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION
  popd
popd
