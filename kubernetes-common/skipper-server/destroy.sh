#!/usr/bin/env bash

if [ -z "$USE_DISTRO_FILES" ]; then
  helm delete scdf --purge || true
fi
