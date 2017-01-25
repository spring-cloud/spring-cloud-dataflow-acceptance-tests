#!/bin/sh

function java_jar() {
    local APP_JAVA_PATH=$1
    local EXPRESSION="nohup ${JAVA_PATH_TO_BIN}java $JAVA_OPTS $MEM_ARGS -jar $APP_JAVA_PATH/scdf-server.jar $APPLICATION_ARGS > $APP_JAVA_PATH/scdf-server.log &"
    echo -e "\nTrying to run [$EXPRESSION]"
    eval ${EXPRESSION}
    pid=$!
    echo ${pid} > ${APP_JAVA_PATH}/app.pid
    echo -e "[$1] process pid is [$pid]"
    echo -e "System props are [$2]"
    echo -e "Logs are under [$1.log] or from nohup [$APP_JAVA_PATH/nohup.log]\n"
    return 0
}
APPLICATION_ARGS="$APPLICATION_ARGS --spring.cloud.dataflow.applicationProperties.stream.security.basic.enabled=false --spring.cloud.dataflow.applicationProperties.stream.management.security.enabled=false"
download $PWD
java_jar $PWD
