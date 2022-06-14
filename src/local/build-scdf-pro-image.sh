#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]
then
  SCDIR="."
fi
if [ "$SCDF_PRO_VERSION" == "" ]
then
  SCDF_PRO_VERSION=1.5.0-SNAPSHOT
fi
pushd "$SCDIR/../../../scdf-pro"
  ./mvnw -o clean install -DskipTests
  pushd scdf-pro-server
    ../mvnw -o spring-boot:build-image -Dspring-boot.build-image.imageName=springcloud/scdf-pro-server:$SCDF_PRO_VERSION
  popd
popd
