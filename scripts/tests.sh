#!/usr/bin/env bash
# Import common functions
. scripts/utility-functions.sh

function print_usage() {
cat <<EOF
USAGE: tests.sh -p <PLATFORM> -b <BINDER> [-d -cc -dv --tests]
  Run the acceptance tests.

Flags:
    -p  | --platform - define the target platform to run, defaults to local
    -b  | --binder - define the binder (i.e. RABBIT, KAFKA) defaults to RABBIT
    --tests - comma separated list of tests to run. Wildcards such as *http* are allowed (e.g. --tests TickTockTests#tickTockTests)
    -cc | --skipCloudConfig - skip Cloud Config server tests for CF
    -se | --schedulesEnabled - run scheduling tests.
    -av | --appsVersion - set the stream app version to test (e.g. Celsius.SR2). Apps should be accessible via maven repo or docker hub.
    -tv | --tasksVersion - set the task app version to test (e.g. Elston.RELEASE). Tasks should be accessible via maven repo or docker hub.
    -c  | --skipCleanup - skip the clean up phase
    -sc | --serverCleanup - run the cleanup for only SCDF and Skipper, along with the applications deployed but excluding the DB, message broker.
[*] = Required arguments
EOF
}

function log_scdf_versions() {
  echo "SCDF SERVER ABOUT:"
  wget --no-check-certificate -O - $SERVER_URI/about | python -m json.tool
}

function log_skipper_versions() {
  echo "SKIPPER SERVER ABOUT:"
  wget --no-check-certificate -O - $SKIPPER_SERVER_URI/api/about | python -m json.tool
}

function run_tests() {
  log_scdf_versions

  log_skipper_versions

  PLATFORM_NAME="default"
  if [ "$PLATFORM" = "cloudfoundry" ]; then
    PLATFORM_NAME="pws"
  fi
#
# Add -Dmaven.surefire.debug to enable remote debugging on port 5005.
#
eval "./mvnw -B -Dspring.profiles.active=blah -Dtest=$TESTS -DPLATFORM_TYPE=$PLATFORM -DNAMESPACE=$KUBERNETES_NAMESPACE \\
  -DSKIP_CLOUD_CONFIG=$skipCloudConfig -Dtest.docker.compose.disable.extension=true -Dtest.docker.compose.dataflowServerUrl=$SERVER_URI \\
  -Dtest.docker.compose.platformName=$PLATFORM_NAME \\
  $MAVEN_PROPERTIES clean test surefire-report:report"
}

echo "Starting $(basename $BASH_SOURCE) $@"

# Rerun failing tests 1 time
rerunFailingTestsCount=1
# Skip after one test fails
skipAfterFailureCount=1


if [[ $1 == "--help" || $1 == "-h" ]] ; then
    print_usage
    exit 0
fi

while [[ $# > 0 ]]
do
key="$1"
case ${key} in
 -p|--platform)
 PLATFORM="$2"
 shift
 ;;
-av|--appsVersion)
 STREAM_APPS_VERSION="$2"
 shift
 ;;
 -tv|--tasksVersion)
 TASK_APPS_VERSION="$2"
 shift
 ;;
-b|--binder)
 BINDER="$2"
 shift
 ;;
 --tests)
 TESTS="$2"
 shift
 ;;
 -cc|--skipCloudConfig)
 skipCloudConfig="true"
 ;;
-se|--schedulesEnabled)
 schedulesEnabled="true"
 ;;
-sc|--serverCleanup)
 serverCleanup="true"
 ;;
 -c|--skipCleanup)
 skipCleanup="true"
 ;;
 --help)
 print_usage
 exit 0
 ;;
 *)
 echo "Invalid option: [$1]"
 print_usage
 exit 1
 ;;
esac
shift
done

[[ ! -d "$PLATFORM" ]] && { echo "$(basename $BASH_SOURCE)  $PLATFORM is an invalid platform"; exit 1; }
[[ ! -d "$PLATFORM/binder/$BINDER" ]] && { echo "$(basename $BASH_SOURCE)  $BINDER is an invalid binder for $PLATFORM platform"; exit 1; }

config

. $PLATFORM/skipper-server/server-uri.sh
. $PLATFORM/server/server-uri.sh

run_tests
# Run clean unless disabled.
if [ -z "$skipCleanup" ]; then
  set -- "$@" -p $PLATFORM
  set -- "$@" -b $BINDER
  if [ "$serverCleanup" ]; then
    set -- "$@" --serverCleanup
  fi
  if [ "$schedulesEnabled" ]; then
      set -- "$@" --schedulesEnabled
  fi
  . scripts/clean.sh "$@"
fi
