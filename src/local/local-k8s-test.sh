#!/usr/bin/env bash
pushd acceptance-tests/custom-apps/timestamp-batch-with-drivers-template1
  ./gradlew build install
popd
# This assumes you are using minikube with helm from bitnami and used release name scdf and executed forward-scdf.sh

DATAFLOW_IP="http://localhost:9393"
BROKER=rabbit
echo "DATAFLOW_IP=$DATAFLOW_IP"
./mvnw -Dspring.profiles.active=blah \
    -DPLATFORM_TYPE=kubernetes \
    -DNAMESPACE=default \
    -DSKIP_CLOUD_CONFIG=true \
    -Dtest.docker.compose.disable.extension=true \
    -Dspring.cloud.dataflow.client.serverUri=$DATAFLOW_IP \
    -Dspring.cloud.dataflow.client.skipSslValidation=true \
    -Dtest=!DataFlowAT#streamAppCrossVersion \
    clean test | tee build.log
