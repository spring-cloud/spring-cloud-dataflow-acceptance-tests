#!/usr/bin/env bash
source ../../$PLATFORM/common.sh
[[ -z "${CERT_URI}" ]] && { echo "CERT_URI is required"; exit 1; }
[[ -z "${DATAFLOW_SERVICE_INSTANCE_NAME}" ]] && { echo "DATAFLOW_SERVICE_INSTANCE_NAME is required"; exit 1; }
[[ -z "${DATAFLOW_SERVICE_NAME}" ]] && { echo "DATAFLOW_SERVICE_NAME is required"; exit 1; }

export DATAFLOW_TILE_CONFIGURATION=$(python3 config_service_instance.py)
echo $(python3 display_tile_config.py)
create_service $DATAFLOW_SERVICE_INSTANCE_NAME $DATAFLOW_SERVICE_NAME $DATAFLOW_PLAN_NAME "$DATAFLOW_TILE_CONFIGURATION"

run_scripts "$PWD" "config.sh"


