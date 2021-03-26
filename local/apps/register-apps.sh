if [ -z "$SERVER_URI" ] ;
then
  export SERVER_URI="http://localhost:9393"
  wait_for_200 ${SERVER_URI}/about
fi

if [[ -z "${STREAM_APPS_URI}" ]]; then
  APPS_BINDER=$BINDER
  if [ "$BINDER" = "rabbit" ]; then
    APPS_BINDER="rabbitmq"
  fi
  export STREAM_APPS_URI="https://dataflow.spring.io/$APPS_BINDER-maven-latest&force=true"
fi
if [[ -z "${TASK_APPS_URI}" ]]; then
  export TASK_APPS_URI="https://dataflow.spring.io/task-maven-latest&force=true"
fi

echo "Register Stream Bulk Apps $STREAM_APPS_URI"
wget -qO- ${SERVER_URI}/apps --post-data="uri=$STREAM_APPS_URI"
echo "Register Task Bulk Apps $TASK_APPS_URI"
wget -qO- ${SERVER_URI}/apps --post-data="uri=$TASK_APPS_URI"
wget -qO- ${SERVER_URI}/apps/task/scenario/0.0.1-SNAPSHOT --post-data="uri=maven://io.spring:scenario-task:0.0.1-SNAPSHOT"
wget -qO- ${SERVER_URI}/apps/task/batch-remote-partition/0.0.1-SNAPSHOT --post-data="uri=maven://org.springframework.cloud.dataflow.acceptence.tests:batch-remote-partition:0.0.1-SNAPSHOT"
wget -qO- ${SERVER_URI}/apps/sink/ver-log/3.0.1 --post-data="uri=maven://org.springframework.cloud.stream.app:log-sink-rabbit:3.0.1"
wget -qO- ${SERVER_URI}/apps/sink/ver-log/2.1.5.RELEASE --post-data="uri=maven://org.springframework.cloud.stream.app:log-sink-rabbit:2.1.5.RELEASE"


echo "APPS REGISTERED"
