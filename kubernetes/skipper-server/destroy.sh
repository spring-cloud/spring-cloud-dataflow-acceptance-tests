#!/usr/bin/env bash

. ../common.sh

if [ -z "$USE_DISTRO_FILES" ]; then
  helm3 delete scdf || true
else
  distro_files_object_delete
fi

