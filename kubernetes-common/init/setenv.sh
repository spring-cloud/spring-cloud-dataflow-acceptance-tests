#!/bin/bash

echo "kubernetes-common contains shared files and not intended to be used as its own target"
if [ -z "$DATAFLOW_SERVER_NAME" ]; then
    DATAFLOW_SERVER_NAME="spring-cloud-dataflow-server-kubernetes"
fi
if [ -z "$SPRING_APPLICATION_JSON" ]; then
    SPRING_APPLICATION_JSON="{ \"maven\": { \"local-repository\": null, \"remote-repositories\": { \"repo1\": { \"url\": \"https://repo.spring.io/libs-snapshot\"} } }, \"spring.cloud.dataflow.application-properties.stream.spring.cloud.stream.bindings.applicationMetrics.destination\": \"metrics\", \"spring.cloud.dataflow.task.platform.kubernetes.accounts.cluster1.memory\" : \"1024Mi\",\"spring.cloud.dataflow.task.platform.kubernetes.accounts.cluster1.createDeployment\" : true }"
fi
exit 1

