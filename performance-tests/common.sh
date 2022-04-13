#!/usr/bin/env bash

get_db_service_instance() {
  service=$(cf services | grep p-dataflow-relational | awk '{print $1}')
  if [ -z "$service" ]; then
    echo "Dataflow Service is not available"
    exit 1
  fi
  echo $service
}

task_wait() {
  local APP_NAME=$1
  local CMD=$2
  local task_id=$(cf run-task $APP_NAME "${CMD}" | grep "task id:" | awk '{print $3}')

  local task_name=$(cf tasks $APP_NAME | grep "^$task_id " | awk '{print $2}')
  local task_status=$(cf tasks $APP_NAME | grep "^$task_id " | awk '{print $3}')
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
