#!/bin/bash

echo -e "\nThe following containers are running:"
docker ps
echo -e "\nWill try to stop kafka if it's running"
docker stop `docker ps | grep "flozano/kafka" | awk '{print $1}'` || echo "No docker with Kafka was running - won't stop anything"

echo -e "\nTrying to run Kafka in Docker\n"
docker run -d -p 2181:2181 -p 9092:9092 --env _KAFKA_advertised_host_name="${DEFAULT_HEALTH_HOST}" --env _KAFKA_advertised_port=9092 flozano/kafka
READY_FOR_TESTS="no"
PORT_TO_CHECK=9092
echo "Waiting for Kafka to boot for [$(( WAIT_TIME * RETRIES ))] seconds"
netcat_port $PORT_TO_CHECK && READY_FOR_TESTS="yes"

if [[ "${READY_FOR_TESTS}" == "no" ]] ; then
    echo "Kafka failed to start..."
    print_logs
    kill_all_apps_if_switch_on
    exit 1
fi

echo -e "\n\nStarting Data Flow apps..."
start_scdf_apps "$SYSTEM_PROPS"
