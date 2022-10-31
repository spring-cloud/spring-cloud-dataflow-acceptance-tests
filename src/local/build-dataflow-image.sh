#!/usr/bin/env bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
if [ "$DATAFLOW_VERSION" = "" ]; then
  DATAFLOW_VERSION=2.10.0-SNAPSHOT
fi
pushd "$SCDIR/../../../spring-cloud-dataflow"  > /dev/null
./mvnw -o clean install -DskipTests
pushd spring-cloud-dataflow-server  > /dev/null
../mvnw -o spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=springcloud/spring-cloud-dataflow-server:$DATAFLOW_VERSION
popd > /dev/null
