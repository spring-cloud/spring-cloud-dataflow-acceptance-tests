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
    -dv | --dataflowVersion - set the dataflow client version to the same as the dataflow server (e.g. 2.5.0.BUILD-SNAPSHOT)
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

  eval "./mvnw -B -Dspring.profiles.active=blah -Dtest=$TESTS -DPLATFORM_TYPE=$PLATFORM -DNAMESPACE=$KUBERNETES_NAMESPACE \\
  -Ddataflow.version=$DATAFLOW_VERSION -DSKIP_CLOUD_CONFIG=$skipCloudConfig test surefire-report:report"
}

echo "Starting $(basename $BASH_SOURCE) $@"

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
-b|--binder)
 BINDER="$2"
 shift
 ;;
-dv|--dataflowVersion)
 DATAFLOW_VERSION="$2"
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
 -s|--skipCleanup)
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

[[ -z "$DATAFLOW_VERSION" ]] && { echo "$(basename $BASH_SOURCE)  'dataflowVersion' must be defined" ; exit 1; }
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