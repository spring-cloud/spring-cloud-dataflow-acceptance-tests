
SCDF_IP=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep server | awk '{print $4}')
while [[ "$SCDF_IP" == "<pending>" ]] ; do
  sleep 1
  SCDF_IP=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep server | awk '{print $4}')
done

if [ "$HTTPS_ENABLED" == "true" ]; then
    export SERVER_URI="https://$SCDF_IP"
else
    export SERVER_URI="http://$SCDF_IP"
fi

if [[ -z "${STREAM_APPS_URI}" ]]; then
  APPS_BINDER=$BINDER
  if [ "$BINDER" = "rabbit" ]; then
    APPS_BINDER="rabbitmq"
  fi
  export STREAM_APPS_URI="https://dataflow.spring.io/$APPS_BINDER-docker-latest&force=true"
fi
if [[ -z "${TASK_APPS_URI}" ]]; then
  export TASK_APPS_URI="https://dataflow.spring.io/task-docker-latest&force=true"
fi

echo "Register Stream Bulk Apps $STREAM_APPS_URI"
wget -qO- ${SERVER_URI}/apps --no-check-certificate --post-data="uri=$STREAM_APPS_URI"
echo "Register Task Bulk Apps $TASK_APPS_URI"
wget -qO- ${SERVER_URI}/apps --no-check-certificate --post-data="uri=$TASK_APPS_URI"
wget -qO- ${SERVER_URI}/apps/task/scenario/0.0.1-SNAPSHOT --no-check-certificate --post-data="uri=docker:springcloudtask/scenario-task:0.0.1-SNAPSHOT"
wget -qO- ${SERVER_URI}/apps/task/batch-remote-partition/0.0.2-SNAPSHOT --no-check-certificate --post-data="uri=docker://springcloud/batch-remote-partition:0.0.2-SNAPSHOT"
wget -qO- ${SERVER_URI}/apps/sink/ver-log/3.0.1 --no-check-certificate --post-data="uri=docker:springcloudstream/log-sink-$BINDER:3.0.1"
wget -qO- ${SERVER_URI}/apps/sink/ver-log/2.1.5.RELEASE --no-check-certificate --post-data="uri=docker:springcloudstream/log-sink-$BINDER:2.1.5.RELEASE"
wget -qO- ${SERVER_URI}/apps/task/task-demo-metrics-prometheus/0.0.4-SNAPSHOT --no-check-certificate --post-data="uri=docker://springcloudtask/task-demo-metrics-prometheus:0.0.4-SNAPSHOT"
wget -qO- ${SERVER_URI}/apps/sink/dataflow-tasklauncher/$DATAFLOW_VERSION --no-check-certificate --post-data="uri=docker:springcloud/spring-cloud-dataflow-tasklauncher-sink-$BINDER:$DATAFLOW_VERSION"

echo "APPS REGISTERED"
