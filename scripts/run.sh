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
    -s  | --skipSetup - skip setup phase
    -t  | --skipTests - skip test phase
    -c  | --skipCleanup - skip the clean up phase

[*] = Required arguments

EOF
}

function load_file() {
filename=$1

while IFS='=' read -r var value; do
  if [ -z ${!var} ]; then
    export $var=$value
  fi
done < "$filename"
}

function test_port() {
  nc -z -w1 ${1} $2
}

function netcat_port() {
    local READY_FOR_TESTS=1
    for i in $( seq 1 "${RETRIES}" ); do
        nc -z -w1 ${1} $2 && READY_FOR_TESTS=0 && break
        echo "Failed to connect to ${1}:$2. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
        sleep "${WAIT_TIME}"
    done
    return ${READY_FOR_TESTS}
}

function download(){
  if [ ! -f "$1/scdf-server.jar" ]; then
    echo "Downloading latest release from $SPRING_CLOUD_DATAFLOW_SERVER_DOWNLOAD_URL"
    wget $SPRING_CLOUD_DATAFLOW_SERVER_DOWNLOAD_URL --progress=bar -O $1/scdf-server.jar
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
    run_scripts "server" "create.sh"
  popd
  echo $SPRING_REDIS_HOST
}

function command_exists() {
  type "$1" &> /dev/null ;
}

function tear_down() {
  echo "Clean up, clean up, everybody everywhere; clean up clean up, everybody do your share!"
  pushd $PLATFORM
    run_scripts "server" "destroy.sh"
    run_scripts "redis" "destroy.sh"
    run_scripts "mysql" "destroy.sh"
    pushd "binder"
      run_scripts $BINDER "destroy.sh"
    popd
  popd
}

function run_tests() {
  echo "Running tests ..."
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
 -p|--platform)
 PLATFORM="$2"
 shift
 ;;
 -b|--binder)
 BINDER="$2"
 shift # past argument
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
[[ -z "${BINDER}" ]] && BINDER=rabbit
WAIT_TIME="${WAIT_TIME:-5}"
RETRIES="${RETRIES:-6}"
JAVA_PATH_TO_BIN="${JAVA_HOME}/bin/"
MEM_ARGS="-Xmx128m -Xss1024k"
JAVA_OPTS=""
APPLICATION_ARGS=""
# ======================================= DEFAULTS END ========================================

if [ -z "$skipSetup" ]; then
  setup
fi
if [ -z "$skipTests" ]; then
  run_tests
fi
if [ -z "$skipCleanup" ]; then
  tear_down
fi
