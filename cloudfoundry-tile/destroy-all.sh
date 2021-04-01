#!/usr/bin/env bash
source $ROOT_DIR/$PLATFORM/common.sh

# Delete any remaing apps before deleting services since some may be bound to them.

for app in $(cf apps | tail +5 | awk '{print $1}'); 
do 
        cf delete -f -r $app 
done

schedulesEnabled=$1
cf delete-service-key -f $DATAFLOW_SERVICE_INSTANCE_NAME $DATAFLOW_SERVICE_KEY
destroy_service $DATAFLOW_SERVICE_INSTANCE_NAME
if [ "$schedulesEnabled" ]; then
        echo "Destroy scheduler"
        run_scripts "scheduler" "destroy.sh"
fi
