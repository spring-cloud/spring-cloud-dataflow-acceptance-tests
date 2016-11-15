#!/bin/bash

set -o errexit

# ======================================= FUNCTIONS START =======================================

# Tails the log
function tail_log() {
    echo -e "\n\nLogs of [$1] jar app"
        tail -n ${NUMBER_OF_LINES_TO_LOG} "$1/$1".log || echo "Failed to open log"
}

# Iterates over active containers and prints their logs to stdout
function print_logs() {
    echo -e "\n\nSomething went wrong... Printing logs:\n"
    docker ps | sed -n '1!p' > /tmp/containers.txt
    while read field1 field2 field3; do
      echo -e "\n\nContainer name [$field2] with id [$field1] logs: \n\n"
      docker logs --tail=${NUMBER_OF_LINES_TO_LOG} -t ${field1}
    done < /tmp/containers.txt
    tail_log "dataflowlib"
}

# ${RETRIES} number of times will try to netcat to passed port $1 and host $2
function netcat_port() {
    local PASSED_HOST="${2:-$HEALTH_HOST}"
    local READY_FOR_TESTS=1
    for i in $( seq 1 "${RETRIES}" ); do
        sleep "${WAIT_TIME}"
        nc -v -w 1 ${PASSED_HOST} $1 && READY_FOR_TESTS=0 && break
        echo "Fail #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds"
    done
    return ${READY_FOR_TESTS}
}

# ${RETRIES} number of times will try to netcat to passed port $1 and localhost
function netcat_local_port() {
    netcat_port $1 "127.0.0.1"
}

# ${RETRIES} number of times will try to curl to /health endpoint to passed port $1 and host $2
function curl_health_endpoint() {
    local PASSED_HOST="${2:-$HEALTH_HOST}"
    local READY_FOR_TESTS=1
    for i in $( seq 1 "${RETRIES}" ); do
        sleep "${WAIT_TIME}"
        curl -m 5 "${PASSED_HOST}:$1/health" && READY_FOR_TESTS=0 && break
        echo "Fail #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds"
    done
    return ${READY_FOR_TESTS}
}

# ${RETRIES} number of times will try to curl to /health endpoint to passed port $1 and localhost
function curl_local_health_endpoint() {
    curl_health_endpoint $1 "127.0.0.1"
}

# Runs the `java -jar` for given application $1 and system properties $2
function java_jar() {
    local APP_JAVA_PATH=$1
    local EXPRESSION="nohup ${JAVA_PATH_TO_BIN}java $2 $MEM_ARGS -jar $APP_JAVA_PATH/*.jar >$APP_JAVA_PATH/$1.log &"
    echo -e "\nTrying to run [$EXPRESSION]"
    eval ${EXPRESSION}
    pid=$!
    echo ${pid} > ${APP_JAVA_PATH}/app.pid
    echo -e "[$1] process pid is [$pid]"
    echo -e "System props are [$2]"
    echo -e "Logs are under [$1.log] or from nohup [$APP_JAVA_PATH/nohup.log]\n"
    return 0
}

# Starts the main brewery apps with given system props $1
function start_scdf_apps() {
    local REMOTE_DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address"
    java_jar "dataflowlib" "$1 $REMOTE_DEBUG=8991"
    return 0
}

function kill_and_log() {
    kill -9 $(cat "$1"/target/app.pid) && echo "Killed $1" || echo "Can't find $1 in running processes"
    pkill -f "$1" && echo "Killed $1 via pkill" ||  echo "Can't find $1 in running processes (tried with pkill)"
}

function kill_all_apps_with_port() {
    kill_app_with_port 9393
}

# port is $1
function kill_app_with_port() {
    kill -9 $(lsof -t -i:$1) && echo "Killed an app running on port [$1]" || echo "No app running on port [$1]"
}

# Kills all started aps
function kill_all_apps() {
    echo `pwd`
    kill_and_log "dataflowlib/spring-cloud-dataflow-server-local"
    kill_all_apps_with_port
    if [[ -z "${KILL_NOW_APPS}" ]] ; then
        docker kill $(docker ps -q) || echo "No running docker containers are left"
        docker stop `docker ps -a -q --filter="image=spotify/kafka"` || echo "No docker with Kafka was running - won't stop anything"
     fi
    return 0
}

# Kills all started aps if the switch is on
function kill_all_apps_if_switch_on() {
    if [[ ${KILL_AT_THE_END} ]]; then
        echo -e "\n\nKilling all the apps"
        kill_all_apps
    else
        echo -e "\n\nNo switch to kill the apps turned on"
        return 0
    fi
    return 0
}

function print_usage() {
cat <<EOF

USAGE:

You can use the following options:

GLOBAL:
-a  |--applogdir - define the location where stream & task logs will be written
-b  |--binder - define the binder to use for the test (i.e. RABBIT, KAFKA)
-j  |--jarurl - which jar to use? Defaults to 1.1.0.BUILD-SNAPSHOT
-h  |--healthhost - what is your host you are running SCDF? where is docker? defaults to localhost
-l  |--numberoflines - how many lines of logs of your app do you want to print? Defaults to 1000
-ke |--killattheend - should kill all the running apps at the end of execution? Defaults to "no"
-n  |--killnow - should not run all the logic but only kill the running apps? Defaults to "no"
-s  |--skipdownloading - should skip downloading the Data Flow Jar. Defaults to "no"
-sb |--skipbinder - should skip starting rabbit docker instance. Defaults to "no"
-d  |--skipdeployment - should skip deployment of apps? Defaults to "no"

EOF
}

# ======================================= FUNCTIONS END =======================================


# ======================================= VARIABLES START =======================================
CURRENT_DIR=`pwd`
WAIT_TIME="${WAIT_TIME:-5}"
RETRIES="${RETRIES:-70}"
DEFAULT_HEALTH_HOST="${DEFAULT_HEALTH_HOST:-127.0.0.1}"
DEFAULT_NUMBER_OF_LINES_TO_LOG="${DEFAULT_NUMBER_OF_LINES_TO_LOG:-1000}"
JAVA_PATH_TO_BIN="${JAVA_HOME}/bin/"
if [[ -z "${JAVA_HOME}" ]] ; then
    JAVA_PATH_TO_BIN=""
fi
LOCALHOST="127.0.0.1"
MEM_ARGS="-Xmx128m -Xss1024k"

# ======================================= VARIABLES END =======================================


# ======================================= PARSING ARGS START =======================================
if [[ $1 == "--help" || $1 == "-h" ]] ; then
    print_usage
    exit 0
fi

while [[ $# > 0 ]]
do
key="$1"
case ${key} in
    -a|--applogdir)
    APP_LOG_DIR="$2"
    shift # past argument
    ;;
    -b|--binder)
    BINDER="$2"
    shift # past argument
    ;;
    -j|--jarurl)
    JAR_URL="$2"
    shift # past argument
    ;;
    -h|--healthhost)
    HEALTH_HOST="$2"
    shift # past argument
    ;;
    -l|--numberoflines)
    NUMBER_OF_LINES_TO_LOG="$2"
    shift # past argument
    ;;
    -ke|--killattheend)
    KILL_AT_THE_END="yes"
    ;;
    -n|--killnow)
    KILL_NOW="yes"
    ;;
    -na|--killnowapps)
    KILL_NOW="yes"
    KILL_NOW_APPS="yes"
    ;;
    -s|--skipdownloading)
    SKIP_DOWNLOADING="yes"
    ;;
    -d|--skipdeployment)
    SKIP_DEPLOYMENT="yes"
    ;;
    -sb|--skipbinder)
    SKIP_BINDER="yes"
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
shift # past argument or value
done

[[ -z "${APP_LOG_DIR}" ]] && APP_LOG_DIR=$CURRENT_DIR/dataflowlib
[[ -z "${BINDER}" ]] && BINDER=RABBIT
[[ -z "${JAR_URL}" ]] && JAR_URL=https://repo.spring.io/libs-snapshot/org/springframework/cloud/spring-cloud-dataflow-server-local/1.1.0.BUILD-SNAPSHOT/spring-cloud-dataflow-server-local-1.1.0.BUILD-SNAPSHOT.jar
[[ -z "${HEALTH_HOST}" ]] && HEALTH_HOST="${DEFAULT_HEALTH_HOST}"
[[ -z "${NUMBER_OF_LINES_TO_LOG}" ]] && NUMBER_OF_LINES_TO_LOG="${DEFAULT_NUMBER_OF_LINES_TO_LOG}"

HEALTH_PORTS=('9393')
HEALTH_ENDPOINTS="$( printf "http://${LOCALHOST}:%s/management/health " "${HEALTH_PORTS[@]}" )"
echo "*************"${APP_LOG_DIR}
ACCEPTANCE_TEST_OPTS="${ACCEPTANCE_TEST_OPTS:--DSERVER_URL=http://${HEALTH_HOST} -DSERVER_PORT=9393 -DAPP_LOG_DIR=${APP_LOG_DIR}}"

cat <<EOF

Running tests with the following parameters

HEALTH_HOST=${HEALTH_HOST}
BINDER=${BINDER}
JAR_URL=${JAR_URL}
NUMBER_OF_LINES_TO_LOG=${NUMBER_OF_LINES_TO_LOG}
KILL_AT_THE_END=${KILL_AT_THE_END}
KILL_NOW=${KILL_NOW}
KILL_NOW_APPS=${KILL_NOW_APPS}
SKIP_DOWNLOADING=${SKIP_DOWNLOADING}
ACCEPTANCE_TEST_OPTS=${ACCEPTANCE_TEST_OPTS}
SKIP_DEPLOYMENT=${SKIP_DEPLOYMENT}
SKIP_BINDER=${SKIP_BINDER}

EOF

# ======================================= PARSING ARGS END =======================================

# ======================================= EXPORTING VARS START =======================================
export BINDER=${BINDER}
export JAR_URL=${JAR_URL}
export HEALTH_HOST=${HEALTH_HOST}
export WAIT_TIME=${WAIT_TIME}
export RETRIES=${RETRIES}
export NUMBER_OF_LINES_TO_LOG=${NUMBER_OF_LINES_TO_LOG}
export KILL_AT_THE_END=${KILL_AT_THE_END}
export KILL_NOW_APPS=${KILL_NOW_APPS}
export LOCALHOST=${LOCALHOST}
export MEM_ARGS=${MEM_ARGS}
export ACCEPTANCE_TEST_OPTS=${ACCEPTANCE_TEST_OPTS}
export SKIP_DEPLOYMENT=${SKIP_DEPLOYMENT}
export SKIP_BINDER=${SKIP_BINDER}
export JAVA_PATH_TO_BIN=${JAVA_PATH_TO_BIN}
export DEFAULT_HEALTH_HOST=${DEFAULT_HEALTH_HOST}

export -f tail_log
export -f print_logs
export -f netcat_port
export -f netcat_local_port
export -f curl_health_endpoint
export -f curl_local_health_endpoint
export -f java_jar
export -f start_scdf_apps
export -f kill_all_apps
export -f kill_and_log
export -f kill_all_apps_with_port
export -f kill_app_with_port

# ======================================= EXPORTING VARS END =======================================

# ======================================= Kill all apps and exit if switch set =======================================
if [[ ${KILL_NOW} ]] ; then
    echo -e "\nKilling all apps"
    kill_all_apps
    exit 0
fi

# ======================================= create dataflowlib directory and get binary=======================================
echo -e "\n\n"
if [[ -z "${SKIP_DOWNLOADING}" ]] ; then
    rm -rf dataflowlib
    mkdir -p dataflowlib
    wget -P dataflowlib ${JAR_URL}
fi

# ======================================= Deploying apps locally  =======================================

INITIALIZATION_FAILED="yes"
if [[ -z "${SKIP_DEPLOYMENT}" ]] ; then
    . ./start-peripherals-${BINDER}.sh && INITIALIZATION_FAILED="no"
else
  INITIALIZATION_FAILED="no"
fi
if [[ "${INITIALIZATION_FAILED}" == "yes" ]] ; then
    echo -e "\n\nFailed to initialize the apps!"
    print_logs
    kill_all_apps_if_switch_on
    exit 1
fi

# ======================================= Checking if apps are booted =======================================
if [[ -z "${SKIP_DEPLOYMENT}" ]] ; then
    # Wait for the apps to boot up
    APPS_ARE_RUNNING="no"

    echo -e "\n\nWaiting for the apps to boot for [$(( WAIT_TIME * RETRIES ))] seconds"
    for i in $( seq 1 "${RETRIES}" ); do
        sleep "${WAIT_TIME}"
        curl -m 5 ${HEALTH_ENDPOINTS} && APPS_ARE_RUNNING="yes" && break
        echo "Fail #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds"
    done

    if [[ "${APPS_ARE_RUNNING}" == "no" ]] ; then
        echo -e "\n\nFailed to boot the apps!"
        print_logs
        kill_all_apps_if_switch_on
        exit 1
    fi

    echo
else
    echo "Skipping deployment"
    READY_FOR_TESTS="yes"
fi

# ======================================= Running acceptance tests =======================================
TESTS_PASSED="no"

if [[ "${READY_FOR_TESTS}" == "yes" ]] ; then
    echo -e "\n\nSuccessfully booted up all the apps. Proceeding with the acceptance tests"
    echo -e "\n\nRunning acceptance tests with the following parameters [${ACCEPTANCE_TEST_OPTS}]"
    ./mvnw clean test $ACCEPTANCE_TEST_OPTS && TESTS_PASSED="yes"
fi

# Check the result of tests execution
if [[ "${TESTS_PASSED}" == "yes" ]] ; then
    echo -e "\n\nTests passed successfully."
    kill_all_apps_if_switch_on
    exit 0
else
    echo -e "\n\nTests failed..."
    print_logs
    kill_all_apps_if_switch_on
    exit 1
fi

