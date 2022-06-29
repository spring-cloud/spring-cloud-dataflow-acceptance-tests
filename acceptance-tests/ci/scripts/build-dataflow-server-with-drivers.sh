#!/bin/bash
set -e
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
source $SCDIR/common.sh
repository=$(pwd)/distribution-repository

pushd git-repo > /dev/null
pushd $BASE_PATH > /dev/null
pushd custom-apps/$APP_TEMPLATE > /dev/null
./gradlew clean build install -Dmaven.repo.local=${repository} -PprojectBuildVersion=$DATAFLOW_VERSION -PspringCloudDataflowVersion=$DATAFLOW_VERSION -PjarPostfix=$APP_VERSION
popd > /dev/null
popd > /dev/null
popd > /dev/null
