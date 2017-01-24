#!/bin/bash

# ======================================= FUNCTIONS START =======================================

function deploy_docker_image() {
  [ -z "$DOCKER_HOST" ] && { echo "Environment variable DOCKER_HOST must be set"; exit 1; }
  DOCKER_SERVER=$(echo $DOCKER_HOST | awk -F/ '{print $3}' | awk -F':' '{print $1}')
  service_running=$(test_port $DOCKER_SERVER "5672")

  if ! command_exists docker-compose; then
    echo "It appears that you don't have a docker command line executable available. Halting."
    exit 1
  fi

  if [[ "$service_running" == 1 ]]; then
    docker-compose up -d rabbitmq
    $(netcat_port $DOCKER_SERVER "5672")
    service_running=$?
  fi

  if [[ "$service_running" == 1 ]]; then
    echo "Could not connect to rabbitMQ running on docker container at $DOCKER_SERVER:5672. "
    exit 1;
  fi


  APPLICATION_ARGS="$APPLICATION_ARGS  --spring.cloud.dataflow.applicationProperties.stream.spring.rabbitmq.host=$DOCKER_SERVER"
  echo "RabbitMQ server running, address: $DOCKER_SERVER"

}

# ======================================= FUNCTIONS END =======================================

$(test_port "127.0.0.1" "5672")
service_running=$?


if [[ "$service_running" == 1 ]]; then
  echo "Could not find a local RabbitMQ server, trying docker."
  deploy_docker_image
fi
