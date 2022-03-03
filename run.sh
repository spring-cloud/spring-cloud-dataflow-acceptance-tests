#!/usr/bin/env bash
#================================= Main script to run test phases setup, test, cleanup, etc =========================
# Import common functions
. scripts/utility-functions.sh

function print_usage() {
cat <<EOF

USAGE: run.sh command [args]

Commands:
   setup: Install and setup test environment
   tests: Run tests
   clean: Clean up test environment
   -h | --help: print this message
EOF
}

if [[ $1 == "--help" || $1 == "-h" ]] ; then
    print_usage
    exit 0
fi

if [[ $# == 0 ]]; then
  print_usage
  exit 0
fi

key="$1"
case ${key} in
 clean)
   shift
   . scripts/clean.sh $@
   exit 0
   ;;
 setup)
   shift
   . scripts/setup.sh $@
   ;;
 tests)
   shift
   . scripts/tests.sh $@
   ;;
 *)
 echo "Invalid task name: [$1]"
 print_usage
 exit 1
 ;;
esac

