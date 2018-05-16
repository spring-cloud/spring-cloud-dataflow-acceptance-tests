#!/bin/bash

# ======================================= FUNCTIONS START =======================================

# ======================================= BE QUIET PUSHD/POPD ===================================
pushd () {
    command pushd "$@" > /dev/null
}

popd () {
    command popd "$@" > /dev/null
}

function print_usage() {
cat <<EOF

USAGE: run.sh -p <PLATFORM> -b <BINDER> [-s -t -c]
  The default mode will setup, run tests and clean up, you can control which stage you want to
  have executed by toggling the flags (-s, -t, -c)

Flags:

[*] -p  | --platform - define the target platform to run
    -b  | --binder - define the binder (i.e. RABBIT, KAFKA) defaults to RABBIT
    -tests - comma separated list of tests to run. Wildcards such as *http* are allowed (e.g. --tests TickTockTests#tickTockTests)
    -s  | --skipSetup - skip setup phase
    -t  | --skipTests - skip test phase
    -c  | --skipCleanup - skip the clean up phase
    -d  | --doNotDownload - skip the downloading of the SCDF/Skipper servers
    -m  | --skipperMode - specify if skipper mode should be enabled
    -cc | --skipCloudConfig - skip Cloud Config server tests for CF
    -sv | --skipperVersion - set the skipper version to test (e.g. 1.0.5.BUILD-SNAPSHOT)
    -dv | --dataflowVersion - set the dataflow version to test (e.g. 1.5.1.BUILD-SNAPSHOT)
    -av | --appsVersion - set the stream app version to test (e.g. Celsius.SR2). Apps should be accessible via maven repo or docker hub.
    -tv | --tasksVersion - set the task app version to test (e.g. Clark.RELEASE). Tasks should be accessible via maven repo or docker hub.

[*] = Required arguments

EOF
}

function load_file() {
filename=$1

echo "Export the unset env. variables from $filename :"
while IFS='=' read -r var value; do
  # only the un-set variables are exported
  if [ -z ${!var} ]; then
    export $var=$(eval echo $value)
    echo "   $var = $(eval echo $value)"
  fi
done < "$filename"
}

function test_port() {
  nc -w1 ${1} $2 >/dev/null
}

function netcat_port() {
    local READY_FOR_TESTS=1
    for i in $( seq 1 "${RETRIES}" ); do
        nc -w1 ${1} $2 </dev/null && READY_FOR_TESTS=0 && break
        echo "Failed to connect to ${1}:$2. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
        sleep "${WAIT_TIME}"
    done
    return ${READY_FOR_TESTS}
}

function download(){
  if [ -z "$doNotDownload" ] || [ ! -f $1/scdf-server.jar ]; then
    if [  ! -z "$doNotDownload" ]; then
      echo "Forcing download since $1/scdf-server.jar was not found"
    fi
    echo "Downloading server from $SPRING_CLOUD_DATAFLOW_SERVER_DOWNLOAD_URL"
    wget $SPRING_CLOUD_DATAFLOW_SERVER_DOWNLOAD_URL --no-verbose -O $1/scdf-server.jar
  else
    echo "Using already downloaded server, waiting for services to start ..."
    sleep 15
  fi
}

function download_skipper(){
  if [ -z "$doNotDownload" ] || [ ! -f $1/skipper-server.jar ]; then
    if [  ! -z "$doNotDownload" ]; then
      echo "Forcing download since $1/skipper-server.jar was not found"
    fi
    echo "Downloading server from $SPRING_CLOUD_SKIPPER_SERVER_DOWNLOAD_URL"
    wget $SPRING_CLOUD_SKIPPER_SERVER_DOWNLOAD_URL --no-verbose -O $1/skipper-server.jar
  else
    echo "Using already downloaded server, waiting for services to start ..."
    sleep 15
  fi
}

function run_scripts()
{
  pushd $1
   . $2
  popd
}

function setup() {
  pushd $PLATFORM
    run_scripts "init" "setenv.sh"
    run_scripts "mysql" "create.sh"
    pushd "binder"
      run_scripts $BINDER "create.sh"
    popd
    run_scripts "redis" "create.sh"
    export SPRING_CLOUD_DATAFLOW_FEATURES_SKIPPER_ENABLED=false
    export SKIPPER_SERVER_URI="http://localhost:7577"

    # Spring Config Server Test (begin)
    if [ "$PLATFORM" == "cloudfoundry" ] && [ -z "$skipCloudConfig" ];
    then
    echo "The Config Server must be pre-started using the config-server/create.sh"
    # Note: to create config server service on PWS run (creation takes couple of minutes!):
    # cf create-service -c '{"git": { "uri": "https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests"}}' p-config-server trial cloud-config-server
    export SPRING_PROFILES_ACTIVE=cloud1
    run_scripts "server" "create.sh"
    SERVER_URI=$(cf apps | grep dataflow-server- | awk '{print $6}' | sed 's:,::g')
    SERVER_URI="http://$SERVER_URI"
    wget $SERVER_URI/about -O about.txt
        # Asserts that the streamsEnabled is false as it was configured in ./scdf-server-cloud1.properties
        if grep -q "{\"analyticsEnabled\":true,\"streamsEnabled\":false,\"tasksEnabled\":true,\"skipperEnabled\":false}" about.txt
            then
            echo "Spring Cloud Config server properties are updated correctly."
            rm about.txt
            else
            echo "Spring Cloud Config server properties are not available for the SCDF server. Tests fails"
            exit 1
        fi
    run_scripts "server" "destroy.sh"
    export SPRING_PROFILES_ACTIVE=cloud
    fi
    # Spring Config Server Test (end)

    if [  ! -z "$skipperMode" ]; then
      export SPRING_CLOUD_DATAFLOW_FEATURES_SKIPPER_ENABLED=true
      run_scripts "skipper-server" "create.sh"
      if [ "$PLATFORM" == "cloudfoundry" ];
      then
      SKIPPER_SERVER_URI=$(cf app skipper-server | grep skipper-server- | awk '{print $2}' | sed 's:,::g')
      export SKIPPER_SERVER_URI="http://$SKIPPER_SERVER_URI"
      echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
      fi
      if [ "$PLATFORM" == "kubernetes" ];
      then
      SKIPPER_SERVER_URI=$(kubectl get svc skipper --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
      export SKIPPER_SERVER_URI="http://$SKIPPER_SERVER_URI:7577"
      echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
      fi
    fi
    run_scripts "server" "create.sh"
  popd
}

function config() {
  pushd $PLATFORM
    run_scripts "init" "setenv.sh"
    pushd "binder"
      run_scripts $BINDER "config.sh"
    popd
    if [  ! -z "$skipperMode" ]; then
      run_scripts "skipper-server" "config.sh"
    fi
    run_scripts "server" "config.sh"
  popd
}

function command_exists() {
  type "$1" &> /dev/null ;
}

function tear_down() {
  echo "Clean up, clean up, everybody everywhere; clean up clean up, everybody do your share!"
  pushd $PLATFORM
    run_scripts "server" "destroy.sh"
    if [  ! -z "$skipperMode" ]; then
      run_scripts "skipper-server" "destroy.sh"
    fi
    run_scripts "redis" "destroy.sh"
    run_scripts "mysql" "destroy.sh"
    pushd "binder"
      run_scripts $BINDER "destroy.sh"
    popd
    cf delete-orphaned-routes -f
  popd
}

function log_scdf_versions() {
  echo "SCDF SERVER ABOUT:"
  wget -q -O - stdout $SERVER_URI/about | python -m json.tool
}

function log_skipper_versions() {
  echo "SKIPPER SERVER ABOUT:"
  wget -q -O - stdout $SKIPPER_SERVER_URI/api/about | python -m json.tool
}

function run_tests() {
  log_scdf_versions
  if [  ! -z "$skipperMode" ]; then
    log_skipper_versions
  fi
  if [  -z "$skipCloudConfig" ]; then
      skipCloudConfig="false"
    fi
  eval "./mvnw -B -Dspring.profiles.active=blah -Dtest=$TESTS -DPLATFORM_TYPE=$PLATFORM -DSKIP_CLOUD_CONFIG=$skipCloudConfig test surefire-report:report"
}

# ======================================= FUNCTIONS END =======================================

CURRENT_DIR=`pwd`

if [[ $1 == "--help" || $1 == "-h" ]] ; then
    print_usage
    exit 0
fi

if [[ $# == 0 ]]; then
  print_usage
  exit 0
fi

while [[ $# > 0 ]]
do
key="$1"
case ${key} in
 -av|--appsVersion)
 STREAM_APPS_VERSION="$2"
 shift
 ;;
 -tv|--tasksVersion)
 TASKS_VERSION="$2"
 shift
 ;;
 -sv|--skipperVersion)
 SKIPPER_VERSION="$2"
 shift
 ;;
 -dv|--dataflowVersion)
 DATAFLOW_VERSION="$2"
 shift
 ;;
 -p|--platform)
 PLATFORM="$2"
 shift
 ;;
 -b|--binder)
 BINDER="$2"
 shift # past argument
 ;;
 -tests)
 TESTS="$2"
 shift
 ;;
 -t|--skipTests)
 skipTests="true"
 ;;
 -s|--skipSetup)
 skipSetup="true"
 ;;
 -c|--skipCleanup)
 skipCleanup="true"
 ;;
 -d|--doNotDownload)
 doNotDownload="false"
 ;;
 -m|--skipperMode)
 skipperMode="true"
 ;;
 -cc|--skipCloudConfig)
 skipCloudConfig="true"
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

# ======================================= DEFAULTS ============================================
[[ -z "${PLATFORM}" ]] && PLATFORM=local
[[ -z "${BINDER}" ]] && BINDER=rabbit
[[ -z "${SKIPPER_VERSION}" ]] && SKIPPER_VERSION=1.0.4.RELEASE
[[ -z "${DATAFLOW_VERSION}" ]] && DATAFLOW_VERSION=1.5.0.BUILD-SNAPSHOT
[[ -z "${STREAM_APPS_VERSION}" ]] && STREAM_APPS_VERSION=Celsius.SR2
[[ -z "${TASKS_VERSION}" ]] && TASKS_VERSION=Clark.RELEASE
WAIT_TIME="${WAIT_TIME:-5}"
RETRIES="${RETRIES:-60}"
JAVA_PATH_TO_BIN="${JAVA_HOME}/bin/"
MEM_ARGS="-Xmx1024m -Xss1024k"
JAVA_OPTS=""
APPLICATION_ARGS=""
# ======================================= DEFAULTS END ========================================
[[ ! -d "$PLATFORM" ]] && { echo "$PLATFORM is an invalid platform"; exit 1; }
[[ ! -d "$PLATFORM/binder/$BINDER" ]] && { echo "$BINDER is an invalid binder for $PLATFORM platform"; exit 1; }

if [ -z "$skipSetup" ]; then
  setup
else
  if [ -z "$skipTests" ] || [ -z "$skipCleanup" ]; then
    config
  fi
fi
if [ -z "$skipTests" ]; then
  run_tests
fi
if [ -z "$skipCleanup" ]; then
  tear_down
fi
