#!/usr/bin/env bash
set -e

n=0
source $(dirname $0)/common.sh
repository=$(pwd)/distribution-repository
triggers=$(pwd)/triggers
buildversion=`date '+%Y-%m-%d-%H-%M-%S'`

pushd git-repo > /dev/null
pushd $BASE_PATH > /dev/null
echo $ARTIFACTORY_PASSWORD | docker login -u $ARTIFACTORY_USERNAME --password-stdin springsource-docker-private-local.jfrog.io
./gradlew clean build \
  -PartifactoryDockerPush=${ARTIFACTORY_DOCKER_PUSH} \
  -PartifactoryDockerLocal=${ARTIFACTORY_DOCKER_LOCAL} \
  -PdataflowIncludeTags="${DATAFLOW_INCLUDE_TAGS}" \
  -PdataflowExcludeTags="${DATAFLOW_EXCLUDE_TAGS}" \
  ${EXTRA_GRADLE_CMDLINE} \
  || n=1
touch ${triggers}/trigger1-${buildversion}
tar -zc --ignore-failed-read --file ${repository}/spring-cloud-dataflow-acceptance-tests-$REPORT_ID-${buildversion}.tar.gz spring-cloud-dataflow-acceptance-tests/build/test-docker-logs
popd > /dev/null
popd > /dev/null

if [ "$n" -gt 0 ]; then
  exit $n
fi
