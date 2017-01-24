#!/bin/bash

# ======================================= FUNCTIONS START =======================================
SERVER_PORT=6379
function deploy_docker_image() {

  [ -z "$DOCKER_HOST" ] && { echo "Environment variable DOCKER_HOST must be set"; exit 1; }
  DOCKER_SERVER=$(echo $DOCKER_HOST | awk -F/ '{print $3}' | awk -F':' '{print $1}')
  if ! command_exists docker-compose; then
    echo "It appears that you don't have a docker command line executable available. Halting."
    exit 1
  fi

  docker-compose up -d redis
  $(netcat_port $DOCKER_SERVER $SERVER_PORT)
  service_running=$?

  if [[ "$service_running" == 1 ]]; then
    echo "Could not connect to redis running on docker container at $DOCKER_SERVER:$SERVER_PORT. "
    exit 1;
  fi

  export SPRING_REDIS_HOST=$DOCKER_SERVER
  echo "redis server running on $DOCKER_SERVER:$SERVER_PORT"
}

# ======================================= FUNCTIONS END =======================================

$(test_port "127.0.0.1" $SERVER_PORT)
service_running=$?


if [[ "$service_running" == 1 ]]; then
  echo "Could not find a local redis server, trying docker."
  deploy_docker_image
fi
