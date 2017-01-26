#!/bin/sh

source ../common.sh

cat << EOF > ./docker-compose.yml
mysql:
  image: mysql:5.7
  environment:
    MYSQL_ROOT_PASSWORD: $SPRING_DATASOURCE_PASSWORD
    MYSQL_DATABASE: $SPRING_DATASOURCE_SCHEMA
    MYSQL_USER: $SPRING_DATASOURCE_USERNAME
    MYSQL_PASSWORD: $SPRING_DATASOURCE_PASSWORD
  ports:
    - 3306:3306
EOF

create "mysql" 3306
echo "Service host is $SERVICE_HOST"
APPLICATION_ARGS="$APPLICATION_ARGS  --spring.datasource.url=jdbc:mysql://$SERVICE_HOST/$SPRING_DATASOURCE_SCHEMA --spring.datasource.username=$SPRING_DATASOURCE_USERNAME --spring.datasource.password=$SPRING_DATASOURCE_PASSWORD  --spring.datasource.driver-class-name=org.mariadb.jdbc.Driver"
