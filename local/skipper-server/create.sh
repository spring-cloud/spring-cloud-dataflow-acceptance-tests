#!/bin/bash

function java_jar() {
    APP_JAVA_PATH=$PWD
    EXPRESSION="nohup ${JAVA_PATH_TO_BIN}java $JAVA_OPTS $MEM_ARGS -jar $APP_JAVA_PATH/skipper-server.jar ${APPLICATION_ARGS} > $APP_JAVA_PATH/skipper-server.log &"
    echo "executing [$EXPRESSION]"
    eval "${EXPRESSION}"
    pid=$!
    echo ${pid} > ${APP_JAVA_PATH}/app.pid
    echo "[$1] process pid is [$pid]"
    echo "System props are [$2]"
    echo "Logs are under [$APP_JAVA_PATH/skipper-server.log] or from nohup [$APP_JAVA_PATH/nohup.log]"
    $(netcat_port localhost 7577)
    return 0
}

run_scripts "$PWD" "config.sh"

if [  ! -z "$skipperMode" ]; then
 APP_LOG_PATH=$PWD/app-logs
 rm -rf $APP_LOG_PATH
 mkdir $APP_LOG_PATH
fi

download_skipper $PWD
java_jar $PWD
