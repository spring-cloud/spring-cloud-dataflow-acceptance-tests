#!/bin/bash


# ======================================= FUNCTIONS START =======================================

function stop_docker_container() {

  if command_exists docker-compose; then
    docker-compose stop
    docker-compose rm -vf
  fi
}

function deploy_docker_container() {

  [ -z "$DOCKER_SERVER" ] && { echo "Environment variable DOCKER_SERVER must be set"; exit 1; }
  if ! command_exists docker-compose; then
    echo "It appears that you don't have a docker-compose command line executable available. Halting."
    exit 1
  fi

  docker-compose up -d
  $(netcat_port $DOCKER_SERVER $SERVER_PORT)
  service_running=$?

  if [[ "$service_running" == 1 ]]; then
    echo "Could not connect to $PROCESS_NAME running on docker container at $DOCKER_SERVER:$SERVER_PORT. "
    exit 1;
  fi

  echo "$PROCESS_NAME running on $DOCKER_SERVER:$SERVER_PORT"
  SERVICE_HOST=$DOCKER_SERVER
}

function create_kafka_docker_compose_file(){
  echo "Creating kafka compose file on $1/docker-compose.yml"
  cat << EOF > $1/docker-compose.yml
  version: '2'
  services:
    zookeeper:
      image: wurstmeister/zookeeper
      ports:
        - "2181:2181"
    kafka:
      image: wurstmeister/kafka:0.10.2.1
      ports:
        - "9092:9092"
      environment:
        KAFKA_ADVERTISED_HOST_NAME: $DOCKER_SERVER
        KAFKA_ADVERTISED_PORT: 9092
        KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
EOF
}


function destroy() {
    stop_docker_container
}

function create() {
  PROCESS_NAME=$1
  SERVER_PORT=$2
  $(test_port "127.0.0.1" $SERVER_PORT)
  service_running=$?


  if [[ "$service_running" == 1 ]]; then
    echo "Could not find a local $PROCESS_NAME server, trying docker."
    deploy_docker_container
  else
    echo "$PROCESS_NAME found on localhost"
    export SERVICE_HOST="127.0.0.1"
  fi
}

# ======================================= FUNCTIONS END =======================================
