#!/bin/bash

function kubectl_create() {
  kubectl create -f secret.yml --namespace $KUBERNETES_NAMESPACE
  sleep 5
  kubectl create -f scdf.yml --namespace $KUBERNETES_NAMESPACE
  READY_FOR_TESTS=1
  for i in $( seq 1 "${RETRIES}" ); do
    SERVER_URI=$(kubectl get svc scdf --namespace $KUBERNETES_NAMESPACE | grep scdf | awk '{print $4}')
    [ '<pending>' != $SERVER_URI ] && READY_FOR_TESTS=0 && break
    echo "Waiting for server external ip. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
    sleep "${WAIT_TIME}"
  done
  SERVER_URI=$(kubectl get svc scdf --namespace $KUBERNETES_NAMESPACE | grep scdf | awk '{print $4}')
  $(netcat_port ${SERVER_URI} 80)
  return 0
}

function generate_manifest() {
cat << EOF > ./scdf.yml
apiVersion: v1
kind: ReplicationController
metadata:
  name: scdf
spec:
  replicas: 1
  selector:
    name: scdf
  template:
    metadata:
      labels:
        name: scdf
    spec:
      containers:
      - name: scdf
        image: springcloud/$DATAFLOW_SERVER_NAME:$DATAFLOW_VERSION
        imagePullPolicy: Always
        ports:
        - containerPort: 80
        resources:
          limits:
            cpu: 1.0
            memory: 2048Mi
          requests:
            cpu: 0.5
            memory: 1024Mi
        env:
        - name: KUBERNETES_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: "metadata.namespace"
        - name: SERVER_PORT
          value: '80'
        - name: SPRING_CLOUD_CONFIG_SERVER_BOOTSTRAP
          value: 'false'
        - name: SPRING_CLOUD_DATAFLOW_FEATURES_ANALYTICS_ENABLED
          value: 'true'
        - name: SPRING_CLOUD_KUBERNETES_SECRETS_ENABLE_API
          value: 'true'
        - name: SPRING_CLOUD_KUBERNETES_SECRETS_NAME
          value: scdf-secrets
        - name: SPRING_CLOUD_KUBERNETES_CONFIG_NAME
          value: scdf-config
        - name: SPRING_CLOUD_DATAFLOW_FEATURES_SKIPPER_ENABLED
          value: '$SPRING_CLOUD_DATAFLOW_FEATURES_SKIPPER_ENABLED'
        - name: SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED
          value: '$SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED'
        - name: SPRING_CLOUD_SKIPPER_CLIENT_SERVER_URI
          value: '$SKIPPER_SERVER_URI/api'
          # Add Maven repo for metadata artifact resolution plus set metrics destination for all stream apps
        - name: SPRING_APPLICATION_JSON
          value: '$SPRING_APPLICATION_JSON'
        - name: KUBERNETES_TRUST_CERTIFICATES
          value: 'true'

---

kind: Service
apiVersion: v1
metadata:
  name: scdf
  labels:
    spring-cloud-service: scdf
spec:
  # If you are running k8s on a local dev box, you can use type NodePort instead
  type: LoadBalancer
  ports:
    - port: 80
  selector:
    name: scdf
EOF

}

if [ -z "$DATAFLOW_SERVER_NAME" ]; then
    DATAFLOW_SERVER_NAME="spring-cloud-dataflow-server-kubernetes"
fi

if [ -z "$SPRING_APPLICATION_JSON" ]; then
    SPRING_APPLICATION_JSON="{ \"maven\": { \"local-repository\": null, \"remote-repositories\": { \"repo1\": { \"url\": \"https://repo.spring.io/libs-snapshot\"} } }, \"spring.cloud.dataflow.application-properties.stream.spring.cloud.stream.bindings.applicationMetrics.destination\": \"metrics\", \"spring.cloud.dataflow.task.platform.kubernetes.accounts.cluster1.memory\" : \"1024Mi\",\"spring.cloud.dataflow.task.platform.kubernetes.accounts.cluster1.createDeployment\" : true }"
fi

RETRIES=20
WAIT_TIME=15
generate_manifest
kubectl_create
run_scripts "$PWD" "config.sh"
