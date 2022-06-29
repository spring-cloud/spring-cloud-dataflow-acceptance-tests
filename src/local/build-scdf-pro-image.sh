#!/usr/bin/env bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
if [ "$SCDF_PRO_VERSION" == "" ]; then
  SCDF_PRO_VERSION=1.5.0-SNAPSHOT
fi
pushd "$SCDIR/../../../scdf-pro"  > /dev/null
./mvnw -o clean install -DskipTests
pushd scdf-pro-server > /dev/null
../mvnw -o spring-boot:build-image  -DskipTests -Dspring-boot.build-image.imageName=springcloud/scdf-pro-server:$SCDF_PRO_VERSION
popd > /dev/null
popd > /dev/null
