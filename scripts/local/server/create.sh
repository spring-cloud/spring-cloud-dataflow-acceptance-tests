#!/bin/bash

function java_jar() {
    local APP_JAVA_PATH=$1
    local EXPRESSION="nohup ${JAVA_PATH_TO_BIN}java $JAVA_OPTS $MEM_ARGS -jar $APP_JAVA_PATH/scdf-server.jar $APPLICATION_ARGS > $APP_JAVA_PATH/scdf-server.log &"
    echo "\nTrying to run [$EXPRESSION]"
    eval ${EXPRESSION}
    pid=$!
    echo ${pid} > ${APP_JAVA_PATH}/app.pid
    echo "[$1] process pid is [$pid]"
    echo "System props are [$2]"
    echo "Logs are under [$APP_JAVA_PATH/scdf-server.log] or from nohup [$APP_JAVA_PATH/nohup.log]\n"
    $(netcat_port localhost 9393)
    return 0
}
APPLICATION_ARGS="$APPLICATION_ARGS --spring.cloud.dataflow.applicationProperties.stream.security.basic.enabled=false --spring.cloud.dataflow.applicationProperties.stream.management.security.enabled=false"
download $PWD
java_jar $PWD
