#!/usr/bin/env bash
SCDIR=$(dirname $0)
if [ "$SCDIR" == "" ]; then
  SCDIR="."
fi
pushd "$SCDIR/../../acceptance-tests/custom-apps/timestamp-batch-with-drivers-template1"  > /dev/null
./gradlew build install
popd > /dev/null # This assumes you are using minikube with helm from bitnami and used release name scdf and executed forward-scdf.sh
if [ "$DATAFLOW_IP" == "" ]; then
  source "$SCDIR/k8s/export-dataflow-ip.sh"
fi
EXTRA=""
if [ "$1" != "" ]; then
  EXTRA="-Dtest=$1"
fi

if [ "$BINDER" == "" ]; then
  export BINDER=rabbit
else
  export BINDER=kafka
fi
if [ "$BINDER" == "kafka" ]; then
  export BROKER=kafka
else
  export BROKER=rabbit
fi
echo "DATAFLOW_IP=$DATAFLOW_IP"
./mvnw -Dspring.profiles.active=blah \
  -DPLATFORM_TYPE=kubernetes \
  -DNAMESPACE=default \
  -DSKIP_CLOUD_CONFIG=true \
  -Dtest.docker.compose.disable.extension=true \
  -Dspring.cloud.dataflow.client.serverUri=$DATAFLOW_IP \
  -Dspring.cloud.dataflow.client.skipSslValidation=true \
  -Dtest=!DataFlowAT#streamAppCrossVersion \
  -X clean test $EXTRA | tee build.log | grep -v -F "DEBUG"
