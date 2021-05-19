#!/usr/bin/env bash

TASK_NAME=task-performance-test-initializer

get_db_service_instance() {
  service=$(cf services | grep p-dataflow-relational | awk '{print $1}')
  if [ -z "$service" ]; then
    echo "Dataflow Service is not available"
    exit 1
  fi
  echo $service
}

create_manifest() {
  DB_SERVICE_INSTANCE=$(get_db_service_instance)
  cat << EOF > ./manifest.yml
applications:
- name: $TASK_NAME
  timeout: 120
  path: ./task-perf-tests-initializer/target/task-performance-tests-initializer-1.1.0.BUILD-SNAPSHOT.jar
  memory: 1G
  health-check: process
  no-route: true
  buildpack: $JAVA_BUILDPACK
  env:
    DATAFLOW_SERVER_URI: $SERVER_URI
    JBP_CONFIG_SPRING_AUTO_RECONFIGURATION: '{ enabled: false }'
    SPRING_PROFILES_ACTIVE: cloud
    SPRING_CLOUD_DATAFLOW_CLIENT_SERVER_URI: $SERVER_URI
    SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_SECRET: $SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_SECRET
    SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_ID: $SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_ID
    SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_TOKEN_URI: $SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_TOKEN_URI
  services:
   - $DB_SERVICE_INSTANCE
EOF
}

task_wait() {
APP_NAME=$1
CMD=$2
echo "running task $APP_NAME with command $CMD"
task_id=$(cf run-task $APP_NAME "${CMD}" | grep "task id:" | awk '{print $3}')

task_name=$(cf tasks $APP_NAME | grep "^$task_id " | awk '{print $2}')
task_status=$(cf tasks $APP_NAME | grep "^$task_id " | awk '{print $3}')

while [ $task_status = 'RUNNING' ]
do
    sleep 1
    task_status=$(cf tasks $APP_NAME | grep "^$task_id " | awk '{print $3}')
done

cf logs $APP_NAME --recent

echo "task $APP_NAME complete with status $task_status"
if [ !$task_status = 'SUCCEEDED' ]; then
  exit 1;
fi

}

# Main

./mvnw clean package -f task-perf-tests-initializer
create_manifest
cf push -i 0
task_wait $TASK_NAME ".java-buildpack/open_jdk_jre/bin/java org.springframework.boot.loader.JarLauncher"
cf delete -f $TASK_NAME






