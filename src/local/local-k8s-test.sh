#!/usr/bin/env bash
if [ "$1" == "-h" ]; then
  echo "Usage $0 <test>"
  echo "  where test:"
  echo "    n for test-n-of-3"
  echo "    info for test-info with aboutTestInfo"
  echo "    groupX for test-groups using provided"
  echo "    otherwise the profile will be test-all with -Dtest and the parameter"
  exit 0
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
pushd "$SCDIR/../../acceptance-tests/custom-apps/timestamp-batch-with-drivers-template1"  > /dev/null
./gradlew build install
popd > /dev/null # This assumes you are using minikube with helm from bitnami and used release name scdf and executed forward-scdf.sh
if [ "$DATAFLOW_IP" == "" ]; then
  echo "DATAFLOW_IP not defined"
  exit 1
fi
if [ "$EXTRA" == "" ]; then
  export EXTRA="-P test-all"
fi
if [ "$1" != "" ]; then
  case $1 in
    1)
    export EXTRA="-P test-1-of-3"
    ;;
    2)
    export EXTRA="-P test-2-of-3"
    ;;
    3)
    export EXTRA="-P test-3-of-3"
    ;;
    info)
    export EXTRA="-P test-info -Dtest=org.springframework.cloud.dataflow.acceptance.test.DataFlowAT#aboutTestInfo"
    ;;
    *)
      if [[ "$1" == *"group"* ]]; then
        export EXTRA="-Dmaven-failsafe-plugin.groups=$*"
      else
        export EXTRA="-Dtest=$*"
      fi
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
echo "EXTRA=$EXTRA" | tee build.log
./mvnw -Dspring.profiles.active=blah \
  -DPLATFORM_TYPE=kubernetes \
  -DNAMESPACE=$NS \
  -DSKIP_CLOUD_CONFIG=true \
  -DBINDER=$BINDER \
  -Dtest.docker.compose.disable.extension=true \
  -Dspring.cloud.dataflow.client.serverUri=$DATAFLOW_IP \
  -Dspring.cloud.dataflow.client.skipSslValidation=true \
  -Dtest=!DataFlowAT#streamAppCrossVersion \
  -X clean test verify $EXTRA | tee -a build.log | grep -v -F "DEBUG"
./mvnw surefire-report:failsafe-report-only
popd
