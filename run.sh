#!/usr/bin/env bash

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
    -sc | --serverCleanup - run the cleanup for the SCDF/Skipper (along with the applications deployed but excluding the DB, message broker)
    -d  | --doNotDownload - skip the downloading of the SCDF/Skipper servers
    -m  | --skipperMode - specify if skipper mode should be enabled
    -cc | --skipCloudConfig - skip Cloud Config server tests for CF
    -dn | --dataflowServerName - set the name of the SCDF server (single server or SCDF implementations from the versions before 2.x)
    -sv | --skipperVersion - set the skipper version to test (e.g. 1.0.5.BUILD-SNAPSHOT)
    -dv | --dataflowVersion - set the dataflow version to test (e.g. 1.5.1.BUILD-SNAPSHOT)
    -av | --appsVersion - set the stream app version to test (e.g. Celsius.SR2). Apps should be accessible via maven repo or docker hub.
    -tv | --tasksVersion - set the task app version to test (e.g. Elston.RELEASE). Tasks should be accessible via maven repo or docker hub.
    -se | --schedulesEnabled - installs scheduling infrastructure and configures SCDF to use the service.
    -na | --noAutoreconfiguration - tell the buildpack to disable spring autoreconfiguration
    -rd | --redisDisabled - disable redis setup and usage
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

function wait_for_200 {
  local READY_FOR_TESTS=1
  for i in $( seq 1 "${RETRIES}" ); do
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' ${1})
    if [ $STATUS -eq 200 ]; then
      READY_FOR_TESTS=0
      break
    else
      echo "Failed to connect to ${1} with status code: $STATUS. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
      sleep "${WAIT_TIME}"
    fi
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
    echo "Using already downloaded server, waiting for server to start ..."
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
    echo "Using already downloaded server, waiting for server to start ..."
    sleep 15
  fi
}

function run_scripts() {
  SCRIPT_DIR=${1}
  SCRIPT_FILE=${2}

  if [[ -d "${SCRIPT_DIR}" ]]; then
    pushd "${SCRIPT_DIR}"

    if [[ -f "${SCRIPT_FILE}" ]]; then
      . ${SCRIPT_FILE}
    else
      echo "Not running non-existent script: ${SCRIPT_DIR}/${SCRIPT_FILE}"
    fi

    popd
  else
    echo "Not running scripts for non-existent script directory: ${SCRIPT_DIR}"
  fi
}

function setup() {
  pushd $PLATFORM
    run_scripts "init" "setenv.sh"
    run_scripts "mysql" "create.sh"
    pushd "binder"
      run_scripts $BINDER "create.sh"
    popd
    if [ ! "$redisDisabled" == "true" ]; then
      run_scripts "redis" "create.sh"
    fi
    if [ "$schedulesEnabled" ]; then
        run_scripts "scheduler" "create.sh"
        export SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED=true
    else
        export SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED=false
    fi
    export SPRING_CLOUD_DATAFLOW_FEATURES_SKIPPER_ENABLED=false
    export SKIPPER_SERVER_URI="http://localhost:7577"

    DOWNLOADED_SERVER=
    # Spring Config Server Test (begin)
    if [ "$PLATFORM" == "cloudfoundry" ] && [ -z "$skipCloudConfig" ];
    then
    echo "NOTE: The Config Server must be pre-started using the config-server/create.sh"
    # Note: to create config server service on PWS run (creation takes couple of minutes!):
    # cf create-service -c '{"git": { "uri": "https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests"}}' $CONFIG_SERVER_SERVICE_NAME $CONFIG_SERVER_PLAN_NAME cloud-config-server
    export SPRING_PROFILES_ACTIVE=cloud1
    echo "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE"
    run_scripts "server" "create.sh"
    echo "Running Config Server test"
    SERVER_URI=$(cf apps | grep dataflow-server- | awk '{print $6}' | sed 's:,::g')
    SERVER_URI="http://$SERVER_URI"
    wget --no-check-certificate $SERVER_URI/about -O about.txt
        # Asserts that the streamsEnabled is false as it was configured in ./scdf-server-cloud1.properties
        if grep -q "\"streamsEnabled\":false,\"tasksEnabled\":true" about.txt
            then
            echo "Spring Cloud Config server properties are updated correctly."
            rm about.txt
            else
            echo "Spring Cloud Config server properties are not available for the SCDF server. Tests fails"
            exit 1
        fi
    run_scripts "server" "destroy.sh"
    echo "Config Server test completed"
    DOWNLOADED_SERVER=true
    export SPRING_PROFILES_ACTIVE=cloud
    fi

    if [ "$PLATFORM" == "cloudfoundry" ];
    then
        export SPRING_PROFILES_ACTIVE=cloud
        echo "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE"
    fi
    # Spring Config Server Test (end)

    if [  ! -z "$skipperMode" ]; then
      export SPRING_CLOUD_DATAFLOW_FEATURES_SKIPPER_ENABLED=true
      run_scripts "skipper-server" "create.sh"
      if [ "$PLATFORM" == "cloudfoundry" ];
      then
      SKIPPER_SERVER_URI=$(cf apps | grep skipper-server- | awk '{print $6}' | sed 's:,::g')
      export SKIPPER_SERVER_URI="http://$SKIPPER_SERVER_URI"
      echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
      fi
      if [ "$PLATFORM" == "kubernetes" ];
      then
        export SKIPPER_SERVER_URI=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
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
    tear_down_servers
    if [ ! "$redisDisabled" == "true" ]; then
      run_scripts "redis" "destroy.sh"
    fi
    run_scripts "mysql" "destroy.sh"
    if [ "$schedulesEnabled" ]; then
        run_scripts "scheduler" "destroy.sh"
    fi
    pushd "binder"
      run_scripts $BINDER "destroy.sh"
    popd
    if [ "$PLATFORM" == "cloudfoundry" ];
    then
      cf delete-orphaned-routes -f
    fi
  popd
}

function tear_down_servers() {
  echo "Clean up servers"
    run_scripts "server" "destroy.sh"
    if [  ! -z "$skipperMode" ]; then
      run_scripts "skipper-server" "destroy.sh"
    fi
    if [ "$PLATFORM" == "cloudfoundry" ];
    then
      cf delete-orphaned-routes -f
    fi
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
  if [  ! -z "$skipperMode" ]; then
    log_skipper_versions
  fi
  if [  -z "$skipCloudConfig" ]; then
    skipCloudConfig="false"
  fi
  eval "./mvnw -B -Dspring.profiles.active=blah -Dtest=$TESTS -DPLATFORM_TYPE=$PLATFORM -DNAMESPACE=$KUBERNETES_NAMESPACE -DSKIP_CLOUD_CONFIG=$skipCloudConfig test surefire-report:report"
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
 -dn|--dataflowServerName)
  DATAFLOW_SERVER_NAME="$2"
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
  -sc|--serverCleanup)
 serverCleanup="true"
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
 -se|--schedulesEnabled)
 schedulesEnabled="true"
 ;;
 -na|--noAutoreconfiguration)
 noAutoreconfiguration="true"
 ;;
 -rd|--redisDisabled)
 redisDisabled="true"
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
[[ -z "${STREAM_APPS_VERSION}" ]] && STREAM_APPS_VERSION=latest
[[ -z "${TASKS_VERSION}" ]] && TASKS_VERSION=latest
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
  if [ -z "$DATAFLOW_VERSION" ]; then
    echo "Data Flow version must be defined"
    exit 1
  fi

  if [ ! -z "$skipperMode" ] && [ -z "$SKIPPER_VERSION" ]; then
    echo "Skipper enabled but Skipper Version not set"
    exit 1
  fi
fi

STREAM_APPS_KAFKA_ARTIFACT_NAME="kafka-10"
if [[ $STREAM_APPS_VERSION == D* ]] || [[ $STREAM_APPS_VERSION == E* ]];
then
  STREAM_APPS_KAFKA_ARTIFACT_NAME="kafka"
fi

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
if [ "$serverCleanup" ]; then
  pushd $PLATFORM
   tear_down_servers
  popd
fi
if [ -z "$skipCleanup" ]; then
  tear_down
fi
