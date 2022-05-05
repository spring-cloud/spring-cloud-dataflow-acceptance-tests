#!/bin/bash
set -e

source $(dirname $0)/common.sh

pushd git-repo > /dev/null
pushd $BASE_PATH > /dev/null
pushd custom-apps/$APP_TEMPLATE > /dev/null
./gradlew clean build install
popd > /dev/null
popd > /dev/null
popd > /dev/null
