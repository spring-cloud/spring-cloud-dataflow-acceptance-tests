#!/usr/bin/env bash
# Import common functions
. scripts/utility-functions.sh

function print_usage() {
cat <<EOF
USAGE: setup.sh -p <PLATFORM> -b <BINDER> [-d -cc -sv -dv -av -tv -se]
  Set up the test environment.

Flags:
    -p  | --platform - define the target platform to run, defaults to local
    -b  | --binder - define the binder (i.e. RABBIT, KAFKA) defaults to RABBIT
    -d  | --doNotDownload - skip the downloading of the SCDF/Skipper servers
    -cc | --skipCloudConfig - skip Cloud Config server tests for CF
    -sv | --skipperVersion - set the skipper version to test (e.g. 1.0.5.BUILD-SNAPSHOT)
    -dv | --dataflowVersion - set the dataflow version to test (e.g. 1.5.1.BUILD-SNAPSHOT)
    -se | --schedulesEnabled - installs scheduling infrastructure and configures SCDF to use the service.
[*] = Required arguments if environment variables are not set.
EOF
}

function download(){
  if [ -z "$DATAFLOW_VERSION" ]; then
    echo "DATAFLOW_VERSION must be defined"
    exit 1
  fi

  DATAFLOW_SERVER_NAME="spring-cloud-dataflow-server"
  SPRING_CLOUD_DATAFLOW_SERVER_DOWNLOAD_URL=https://repo.spring.io/libs-snapshot/org/springframework/cloud/$DATAFLOW_SERVER_NAME/$DATAFLOW_VERSION/$DATAFLOW_SERVER_NAME-$DATAFLOW_VERSION.jar

  if [ -z "$doNotDownload" ] || [ ! -f $1/scdf-server.jar ]; then
    if [  ! -z "$doNotDownload" ]; then
      echo "Forcing download since $1/scdf-server.jar was not found"
    fi
    echo "Downloading server from $SPRING_CLOUD_DATAFLOW_SERVER_DOWNLOAD_URL"
    wget $SPRING_CLOUD_DATAFLOW_SERVER_DOWNLOAD_URL --no-verbose -O $1/scdf-server.jar
  else
    echo "Using already downloaded server, waiting for server to start ..."
    . $PLATFORM/server/server-uri.sh
  fi
}

function download_skipper(){
  if [ -z "$SKIPPER_VERSION" ]; then
    echo "SKIPPER_VERSION must be defined"
    exit 1
  fi
  SPRING_CLOUD_SKIPPER_SERVER_DOWNLOAD_URL=https://repo.spring.io/libs-snapshot/org/springframework/cloud/spring-cloud-skipper-server/$SKIPPER_VERSION/spring-cloud-skipper-server-$SKIPPER_VERSION.jar
  if [ -z "$doNotDownload" ] || [ ! -f $1/skipper-server.jar ]; then
    if [  ! -z "$doNotDownload" ]; then
      echo "Forcing download since $1/skipper-server.jar was not found"
    fi
    echo "Downloading server from $SPRING_CLOUD_SKIPPER_SERVER_DOWNLOAD_URL"
    wget $SPRING_CLOUD_SKIPPER_SERVER_DOWNLOAD_URL --no-verbose -O $1/skipper-server.jar
  else
    echo "Using already downloaded server, waiting for server to start ..."
    . $PLATFORM/skipper-server/server-uri.sh
  fi
}

function setup() {
  pushd $PLATFORM
    run_scripts "init" "setenv.sh"
    run_scripts "mysql" "create.sh"
    pushd "binder"
      run_scripts $BINDER "create.sh"
    popd
    if [ "$schedulesEnabled" ]; then
        run_scripts "scheduler" "create.sh"
        export SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED=true
    else
        export SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED=false
    fi

    DOWNLOADED_SERVER=
    # Spring Config Server Test (begin)
    #TODO - Remove platform specific here
    if [ "$PLATFORM" == "cloudfoundry" ] && [ -z "$skipCloudConfig" ];
    then
    echo "NOTE: The Config Server must be pre-started using the config-server/create.sh"
    # Note: to create config server service on PWS run (creation takes couple of minutes!):
    # cf create-service -c '{"git": { "uri": "https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests"}}' $CONFIG_SERVER_SERVICE_NAME $CONFIG_SERVER_PLAN_NAME cloud-config-server
    export SPRING_PROFILES_ACTIVE=cloud1
    echo "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE"
    run_scripts "server" "create.sh"
    echo "Running Config Server test"
    . ./$PLATFORM/server/server-uri.sh
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

    DEBUG "setup create skipper-server $DATAFLOW_VERSION"
    run_scripts "skipper-server" "create.sh"
    DEBUG "setup create server $DATAFLOW_VERSION"
    run_scripts "server" "create.sh"
  popd
}

function config() {
  pushd $PLATFORM
    run_scripts "init" "setenv.sh"
    pushd "binder"
      run_scripts $BINDER "config.sh"
    popd

    run_scripts "skipper-server" "config.sh"

    run_scripts "server" "config.sh"
  popd
}

function command_exists() {
  type "$1" &> /dev/null ;
}

# ======================================= FUNCTIONS END =======================================
# ======================================= Main =======================================

echo "Starting $(basename $BASH_SOURCE) $@"

if [[ $1 == "--help" || $1 == "-h" ]] ; then
    print_usage
    exit 0
fi

while [[ $# > 0 ]]
do
key="$1"
case ${key} in
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
 -d|--doNotDownload)
 doNotDownload="false"
 ;;
 -cc|--skipCloudConfig)
 skipCloudConfig="true"
 ;;
 -se|--schedulesEnabled)
 schedulesEnabled="true"
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

[[ ! -d "$PLATFORM" ]] && { echo "$(basename $BASH_SOURCE) $PLATFORM is an invalid platform"; exit 1; }
[[ ! -d "$PLATFORM/binder/$BINDER" ]] && { echo "$(basename $BASH_SOURCE) $BINDER is an invalid binder for $PLATFORM platform"; exit 1; }

setup

