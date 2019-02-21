#!/usr/bin/env bash

source ../common.sh

cat << EOF > ./docker-compose.yml
mysql:
  image: mysql:5.7
  environment:
    MYSQL_ROOT_PASSWORD: $SPRING_DATASOURCE_PASSWORD
    MYSQL_DATABASE: $SPRING_SCHEMA_NAME
    MYSQL_USER: $SPRING_DATASOURCE_USERNAME
    MYSQL_PASSWORD: $SPRING_DATASOURCE_PASSWORD
  ports:
    - 3306:3306
EOF

echo "cleaning up any previous mysql docker containers..."
docker ps -q --filter ancestor="mysql:5.7" | xargs -r docker stop
echo "done cleaning up."
create "mysql" 3306
echo "Service host is $SERVICE_HOST"
APPLICATION_ARGS="$APPLICATION_ARGS --spring.datasource.driver-class-name=org.mariadb.jdbc.Driver --spring.datasource.url=jdbc:mysql://$SERVICE_HOST/$SPRING_SCHEMA_NAME --spring.datasource.username=$SPRING_DATASOURCE_USERNAME --spring.datasource.password=$SPRING_DATASOURCE_PASSWORD"
