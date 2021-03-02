if [ -z "$SERVER_URI" ] ;
then
    SERVER_URI=$(cf apps | grep dataflow-server- | awk '{print $6}' | sed 's:,::g')
    export SERVER_URI="https://$SERVER_URI"
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
curl -X POST ${SERVER_URI}/apps -d "uri=$STREAM_APPS_URI" -k
echo "Register Task Bulk Apps $TASK_APPS_URI"
curl -X POST ${SERVER_URI}/apps -d "uri=$TASK_APPS_URI" -k
curl -X POST ${SERVER_URI}/apps/task/scenario/0.0.1-SNAPSHOT -d "uri=maven://io.spring:scenario-task:0.0.1-SNAPSHOT" -k
curl -X POST ${SERVER_URI}/apps/task/batch-remote-partition/0.0.1-SNAPSHOT -d "uri=maven://org.springframework.cloud.dataflow.acceptence.tests:batch-remote-partition:0.0.1-SNAPSHOT" -k

echo "APPS REGISTERED"
