#!/bin/bash

function java_jar() {
    APP_JAVA_PATH=$PWD
    EXPRESSION="nohup ${JAVA_PATH_TO_BIN}java $JAVA_OPTS $MEM_ARGS -jar $APP_JAVA_PATH/scdf-server.jar ${APPLICATION_ARGS} > $APP_JAVA_PATH/scdf-server.log &"
    echo "executing [$EXPRESSION]"
    eval "${EXPRESSION}"
    pid=$!
    echo ${pid} > ${APP_JAVA_PATH}/app.pid
    echo "[$1] process pid is [$pid]"
    echo "System props are [$2]"
    echo "Logs are under [$APP_JAVA_PATH/scdf-server.log] or from nohup [$APP_JAVA_PATH/nohup.log]"
    $(netcat_port localhost 9393)
    return 0
}
export APPLICATION_ARGS="$APPLICATION_ARGS --spring.cloud.dataflow.applicationProperties.stream.security.basic.enabled=false"
download $PWD
java_jar $PWD
