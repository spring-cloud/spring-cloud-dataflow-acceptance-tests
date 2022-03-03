#!/usr/bin/env bash
export ROOT_DIR=$PWD
# Import common functions
. scripts/utility-functions.sh

function print_usage() {
cat <<EOF
USAGE: tests.sh -p <PLATFORM> -b <BINDER> [-pf -d -cc -dv --tests]
  Run the acceptance tests.

Flags:
    -p  | --platform - define the target platform to run, defaults to local
    -pf | --platformFolder - folder containing the scripts for installing the platform. Defaults to 'platform'
    -b  | --binder - define the binder (i.e. RABBIT, KAFKA) defaults to RABBIT
    --tests - comma separated list of tests to run. Wildcards such as *http* are allowed (e.g. --tests TickTockTests#tickTockTests)
    -cc | --skipCloudConfig - skip Cloud Config server tests for CF
    -se | --schedulesEnabled - run scheduling tests.
    -av | --appsVersion - set the stream app version to test (e.g. Celsius.SR2). Apps should be accessible via maven repo or docker hub.
    -tv | --tasksVersion - set the task app version to test (e.g. Elston.RELEASE). Tasks should be accessible via maven repo or docker hub.
    -c  | --skipCleanup - skip the clean up phase
    -sc | --serverCleanup - run the cleanup for only SCDF and Skipper, along with the applications deployed but excluding the DB, message broker.
    -ss | --skipSslValidation - skip SSL validation.
    -hs | --httpsEnabled - uses HTTPS urls to connect to deployed Stream and Task apps (k8s only).
[*] = Required arguments
EOF
}

function log_scdf_versions() {
  echo "SCDF SERVER ABOUT:"
  curl -k -H "Authorization: $(cf oauth-token)" $SERVER_URI/about | python -m json.tool
}

function log_skipper_versions() {
  echo "SKIPPER SERVER ABOUT:"
  wget --no-check-certificate -O - $SKIPPER_SERVER_URI/api/about | python -m json.tool
}

function run_tests() {
  log_scdf_versions

  log_skipper_versions

 [ -z  "$PLATFORM_NAME" ] && PLATFORM_NAME="default"

# Add -Dmaven.surefire.debug to enable remote debugging on port 5005.
#
eval "./mvnw -U -B -Dspring.profiles.active=blah -Dtest=$TESTS -DPLATFORM_TYPE=$PLATFORM -DNAMESPACE=$KUBERNETES_NAMESPACE \\
  -DSKIP_CLOUD_CONFIG=$skipCloudConfig -Dtest.docker.compose.disable.extension=true -Dspring.cloud.dataflow.client.serverUri=$SERVER_URI \\
  -Dspring.cloud.dataflow.client.skipSslValidation=$skipSslValidation -Dtest.platform.connection.platformName=$PLATFORM_NAME \\
  -Dtest.platform.connection.applicationOverHttps=$HTTPS_ENABLED \\
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

skipSslValidation="false"
HTTPS_ENABLED="false"

while [[ $# > 0 ]]
do
key="$1"
case ${key} in
 -p|--platform)
 PLATFORM="$2"
 shift
 ;;
 -pf|--platformFolder)
 PLATFORM_FOLDER="$2"
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
 -ss|--skipSslValidation)
 skipSslValidation="true"
 ;;
 -hs|--httpsEnabled)
 HTTPS_ENABLED="true"
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

[[ -z "${PLATFORM_FOLDER}" ]] && PLATFORM_FOLDER=$PLATFORM

[[ ! -d "$PLATFORM_FOLDER" ]] && { echo "$(basename $BASH_SOURCE)  $PLATFORM_FOLDER is an invalid platform folder"; exit 1; }
[[ ! -d "$PLATFORM_FOLDER/binder/$BINDER" ]] && { echo "$(basename $BASH_SOURCE)  $BINDER is an invalid binder for $PLATFORM_FOLDER platform folder"; exit 1; }

config

. $PLATFORM_FOLDER/skipper-server/server-uri.sh
. $PLATFORM_FOLDER/server/server-uri.sh

run_tests
status=$?
if [ $status -ne 0 ]
then
  exit $status
fi

# Run clean unless disabled.
if [ -z "$skipCleanup" ]; then
  set -- "$@" -p $PLATFORM
  set -- "$@" -pf $PLATFORM_FOLDER
  set -- "$@" -b $BINDER
  if [ "$serverCleanup" ]; then
    set -- "$@" --serverCleanup
  fi
  if [ "$schedulesEnabled" ]; then
      set -- "$@" --schedulesEnabled
  fi
  . scripts/clean.sh "$@"
fi
exit
