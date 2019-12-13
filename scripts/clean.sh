#!/usr/bin/env bash
#================================= Clean up target AT environment =========================
# Import common functions
. scripts/utility-functions.sh

function print_usage() {
cat <<EOF

USAGE: clean.sh -p <PLATFORM> -b <BINDER> [--schedulesEnabled]
  This will cleanup any existing resources on the platform

Flags:

-p  | --platform - define the target platform to clean, defaults to local
-b  | --binder - define the binder (i.e. RABBIT, KAFKA) defaults to RABBIT
-sc | --serverCleanup - run the cleanup for only SCDF and Skipper, along with the applications deployed but excluding the DB, message broker.
-se | --schedulesEnabled - cleans the scheduling infrastructure.
EOF
}

function tear_down() {
  echo "Clean up, clean up, everybody everywhere; clean up clean up, everybody do your share!"

  tear_down_servers

  pushd $PLATFORM
    run_scripts "mysql" "destroy.sh"
    if [ "$schedulesEnabled" ]; then
        run_scripts "scheduler" "destroy.sh"
    fi
    pushd "binder"
      run_scripts $BINDER "destroy.sh"
    popd
    #TODO: Remove platform specific logic
    if [ "$PLATFORM" == "cloudfoundry" ];
    then
      cf delete-orphaned-routes -f
    fi
  popd
}

function tear_down_servers() {
  pushd $PLATFORM
    echo "Clean up servers"
    run_scripts "server" "destroy.sh"

    run_scripts "skipper-server" "destroy.sh"
    #TODO: Remove platform specific logic
    if [ "$PLATFORM" == "cloudfoundry" ];
    then
      cf delete-orphaned-routes -f
    fi
  popd
}
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
 -p|--platform)
 PLATFORM="$2"
 shift
 ;;
-b|--binder)
 BINDER="$2"
 shift
 ;;
-se|--schedulesEnabled)
 schedulesEnabled="true"
 ;;
 -sc|--serverCleanup)
 serverCleanup="true"
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

pushd $PLATFORM
    run_scripts "init" "setenv.sh"
popd

if [ "$serverCleanup" ]; then
  tear_down_servers
else
  tear_down
fi



