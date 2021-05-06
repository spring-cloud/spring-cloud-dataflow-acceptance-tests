#!/usr/bin/env bash
source $ROOT_DIR/$PLATFORM/common.sh
run_scripts server destroy.sh
schedulesEnabled=$1
if [ "$schedulesEnabled" ]; then
        echo "Destroy scheduler"
        run_scripts "scheduler" "destroy.sh"
fi
cf delete-service-key -f $DATAFLOW_SERVICE_INSTANCE_NAME $DATAFLOW_SERVICE_KEY
destroy_service $DATAFLOW_SERVICE_INSTANCE_NAME

