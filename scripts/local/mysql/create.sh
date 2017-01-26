#!/bin/sh

source ../common.sh

create "mysql" 3306

APPLICATION_ARGS="$APPLICATION_ARGS  --spring.datasource.url=jdbc:mysql://$SERVICE_HOST/scdf --spring.datasource.username=spring --spring.datasource.password=cloud  --spring.datasource.driver-class-name=org.mariadb.jdbc.Driver"
