#!/usr/bin/env bash
if [ "$DATAFLOW_VERSION" == "" ]
then
  DATAFLOW_VERSION=2.10.0-SNAPSHOT
fi
pushd ../spring-cloud-dataflow
  ./mvnw -o clean install -DskipTests
  pushd spring-cloud-dataflow-server
    ../mvnw -o spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=springcloud/spring-cloud-dataflow-server:$DATAFLOW_VERSION
  popd
popd
