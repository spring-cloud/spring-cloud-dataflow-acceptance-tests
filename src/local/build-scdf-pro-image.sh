#!/usr/bin/env bash
if [ "$SCDF_PRO_VERSION" == "" ]
then
  SCDF_PRO_VERSION=1.5.0-SNAPSHOT
fi
pushd ../scdf-pro
  ./mvnw -o clean install -DskipTests
  pushd scdf-pro-server
    ../mvnw -o spring-boot:build-image -Dspring-boot.build-image.imageName=springcloud/scdf-pro-server:$SCDF_PRO_VERSION
  popd
popd
