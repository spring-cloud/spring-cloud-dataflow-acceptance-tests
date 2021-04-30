#!/usr/bin/env bash
# ======================================= DEFAULTS ============================================
[[ -z "${PLATFORM}" ]] && PLATFORM=local
[[ -z "${PLATFORM_FOLDER}" ]] && PLATFORM_FOLDER=$PLATFORM
[[ -z "${BINDER}" ]] && BINDER=rabbit
WAIT_TIME="${WAIT_TIME:-5}"
RETRIES="${RETRIES:-60}"
JAVA_PATH_TO_BIN="${JAVA_HOME}/bin/"
MEM_ARGS="-Xmx1024m -Xss1024k"
JAVA_OPTS=""
APPLICATION_ARGS=""
STREAM_APPS_VERSION="latest"
TASK_APPS_VERSION="latest"
# ======================================= FUNCTIONS START =======================================
# ======================================= BE QUIET PUSHD/POPD ===================================

DEBUG() {
  if [ -n "$DEBUG" ];
  then
    echo "DEBUG -- $1"
  fi
}

pushd () {
    command pushd "$@" > /dev/null
}

popd () {
    command popd "$@" > /dev/null
}

load_file() {
  filename=$1
  echo "Export the unset env. variables from $filename :"
  while IFS='=' read -r var value; do
  if ! [[ $var == \#* ]]; then
    # only the un-set variables are exported
    if [ -z ${!var} ]; then
      export $var="$(eval echo $value)"
    fi
  fi
  done < "$filename"
}

test_port() {
  nc -w2 ${1} $2 >/dev/null
}

netcat_port() {
    local READY_FOR_TESTS=1
    for i in $( seq 1 "${RETRIES}" ); do
        nc -w2 ${1} $2 </dev/null && READY_FOR_TESTS=0 && break
        echo "Failed to connect to ${1}:$2. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
        sleep "${WAIT_TIME}"
    done
    return ${READY_FOR_TESTS}
}

wait_for_200() {
  OLDOPTS=$(set +o)
  #Ensure errexit is disable.
  set +e
  local READY_FOR_TESTS=1

  for i in $( seq 1 "${RETRIES}" ); do
   if [ $# -gt 1 ]; then
      STATUS=`curl -k -s -H"$2"  -o /dev/null -w %{http_code} $1`
    else
      STATUS=`curl -k -s -o /dev/null -w %{http_code} $1`
    fi
    DEBUG "Status: $STATUS"
    if [ $STATUS -eq 200 ]; then
      DEBUG "READY FOR TESTS"
      READY_FOR_TESTS=0
      break
    else
      if [ $STATUS -eq 401 ]; then
         break
      fi
      echo "Failed to connect to ${1} with status code: $STATUS. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
      sleep "${WAIT_TIME}"
    fi
  done
  #Restore previous errexit flag.
  case $- in
    *e*) OLDOPTS="$OLDOPTS; set -e";;
      *) OLDOPTS="$OLDOPTS; set +e";;
  esac
  if [ $READY_FOR_TESTS -ne 0 ]; then
    set -e
    echo "FATAL: Unable to connect to $1"
    exit 1
  fi
}

run_scripts() {
  SCRIPT_DIR=${1}
  SCRIPT_FILE=${2}
  CURRENT_DIR=${PWD##*/}

  if [[ -d "${SCRIPT_DIR}" ]]; then
    pushd "${SCRIPT_DIR}"

    if [[ -f "${SCRIPT_FILE}" ]]; then
      DEBUG "executing $CURRENT_DIR/$SCRIPT_DIR/$SCRIPT_FILE"
      . ${SCRIPT_FILE} ${@:3:$#}
    else
      echo "Not running non-existent script: ${SCRIPT_DIR}/${SCRIPT_FILE}"
    fi

    popd
  else
    echo "Not running scripts for non-existent script directory: ${SCRIPT_DIR}"
  fi
}

command_exists() {
  type "$1" &> /dev/null ;
}

config() {
  pushd $PLATFORM_FOLDER
    run_scripts "init" "setenv.sh"
    pushd "binder"
      run_scripts $BINDER "config.sh"
    popd

    run_scripts "skipper-server" "config.sh"

    run_scripts "server" "config.sh"
  popd
}
