#!/bin/sh

# ======================================= FUNCTIONS START =======================================

function deploy_docker_image() {
  [ -z "$DOCKER_HOST" ] && { echo "Environment variable DOCKER_HOST must be set"; exit 1; }
  DOCKER_SERVER=$(echo $DOCKER_HOST | awk -F/ '{print $3}' | awk -F':' '{print $1}')
  if ! command_exists docker-compose; then
    echo "It appears that you don't have a docker command line executable available. Halting."
    exit 1
  fi
  docker-compose up -d mysql
  $(netcat_port $DOCKER_SERVER "3306")
  service_running=$?

  if [[ "$service_running" == 1 ]]; then
    echo "Could not connect to mySQL running on docker container at $DOCKER_SERVER:3306. "
    exit 1;
  fi

  APPLICATION_ARGS="$APPLICATION_ARGS  --spring.datasource.url=jdbc:mysql://$DOCKER_SERVER/scdf --spring.datasource.username=spring --spring.datasource.password=cloud  --spring.datasource.driver-class-name=org.mariadb.jdbc.Driver"

  echo "mySQL server running. JDBC URL:$SPRING_DATASOURCE_URL"
}

# ======================================= FUNCTIONS END =======================================

$(test_port "127.0.0.1" "3306")
service_running=$?


if [[ "$service_running" == 1 ]]; then
  echo "Could not find a local mySQL server, trying docker."
  deploy_docker_image
fi
