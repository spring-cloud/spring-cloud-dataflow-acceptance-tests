#!/bin/bash

SYSTEM_PROPS="-DRABBIT_HOST=${HEALTH_HOST} -Dspring.rabbitmq.port=5672 -Dspring.zipkin.enabled=false -Dspring.profiles.active=eureka"

dockerComposeFile="docker-compose-RABBIT.yml"

if [[ -z "${SKIP_BINDER}" ]] ; then
    docker-compose -f $dockerComposeFile kill
    docker-compose -f $dockerComposeFile build

    echo -e "\n\nBooting up RabbitMQ"
    docker-compose -f $dockerComposeFile up -d rabbitmq
fi

READY_FOR_TESTS="no"
PORT_TO_CHECK=5672
echo "Waiting for RabbitMQ to boot for [$(( WAIT_TIME * RETRIES ))] seconds"
netcat_port $PORT_TO_CHECK && READY_FOR_TESTS="yes"

if [[ "${READY_FOR_TESTS}" == "no" ]] ; then
    echo "RabbitMQ failed to start..."
    exit 1
fi


echo -e "\n\nStarting Data Flow apps..."
start_scdf_apps "$SYSTEM_PROPS"
