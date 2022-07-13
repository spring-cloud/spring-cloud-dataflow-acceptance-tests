#!/usr/bin/env bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
pushd "$SCDIR/../../acceptance-tests/custom-apps/timestamp-batch-with-drivers-template1"  > /dev/null
./gradlew build install
popd > /dev/null # This assumes you are using minikube with helm from bitnami and used release name scdf and executed forward-scdf.sh
if [ "$DATAFLOW_IP" == "" ]; then
  echo "DATAFLOW_IP not defined"
  exit 1
fi
EXTRA="-P test-all"
if [ "$1" != "" ]; then
  case $1 in
    1)
    EXTRA="-P test-1-of-3"
    ;;
    2)
    EXTRA="-P test-2-of-3"
    ;;
    3)
    EXTRA="-P test-3-of-3"
    ;;
    info)
    EXTRA="-P test-info"
    ;;
    *)
    EXTRA="-P test-all -Dtest=$1"
  esac
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
pushd $(realpath $SCDIR/../..)
./mvnw -Dspring.profiles.active=blah \
  -DPLATFORM_TYPE=kubernetes \
  -DNAMESPACE=default \
  -DSKIP_CLOUD_CONFIG=true \
  -Dtest.docker.compose.disable.extension=true \
  -Dspring.cloud.dataflow.client.serverUri=$DATAFLOW_IP \
  -Dspring.cloud.dataflow.client.skipSslValidation=true \
  -Dtest=!DataFlowAT#streamAppCrossVersion \
  -X clean test $EXTRA | tee build.log | grep -v -F "DEBUG"
popd
