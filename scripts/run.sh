#!/bin/bash

set -o errexit

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
USAGE:
You can use the following options:
GLOBAL:
[*] -p  | --platform - define the target platform to run
    -b  | --binder - define the binder to use for the test (i.e. RABBIT, KAFKA)
    -s  | --skip - skip tests and just prepares environment
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
}

function tear_down() {
  pushd $PLATFORM
    run_scripts "server" "destroy.sh"
    run_scripts "redis" "destroy.sh"
    run_scripts "mysql" "destroy.sh"
    pushd "binder"
      run_scripts $BINDER "destroy.sh"
    popd
  popd
}
# ======================================= FUNCTIONS END =======================================

CURRENT_DIR=`pwd`

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
 shift # past argument
 ;;
 -s|--skipTests)
 skip="true"
 shift # past argument
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

[[ -z "${BINDER}" ]] && BINDER=rabbit


setup
