#!/bin/bash

function kubectl_create() {
  kubectl create -f skipper.yml --namespace $KUBERNETES_NAMESPACE
  READY_FOR_TESTS=1
  for i in $( seq 1 "${RETRIES}" ); do
    SKIPPER_SERVER_URI=$(kubectl get svc skipper --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
    [ '<pending>' != $SKIPPER_SERVER_URI ] && READY_FOR_TESTS=0 && break
    echo "Waiting for skipper server external ip. Attempt  #$i/${RETRIES}... will try again in [${WAIT_TIME}] seconds" >&2
    sleep "${WAIT_TIME}"
  done
  SKIPPER_SERVER_URI=$(kubectl get svc skipper --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
  $(netcat_port ${SKIPPER_SERVER_URI} 7577)
  return 0
}

function generate_manifest() {
cat << EOF > ./skipper.yml
apiVersion: v1
kind: ReplicationController
metadata:
  name: skipper
spec:
  replicas: 1
  selector:
    name: skipper
  template:
    metadata:
      labels:
        name: skipper
    spec:
      containers:
      - name: skipper
        image: $SKIPPER_SERVER_IMAGE
        imagePullPolicy: Always
        ports:
        - containerPort: 7577
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
          value: '7577'
          # Add Maven repo for metadata artifact resolution
        - name: SPRING_APPLICATION_JSON
          value: "{ \"maven\": { \"local-repository\": null, \"remote-repositories\": { \"repo1\": { \"url\": \"https://repo.spring.io/libs-snapshot\"} } } }"

---

kind: Service
apiVersion: v1
metadata:
  name: skipper
  labels:
    spring-cloud-service: skipper
spec:
  # If you are running k8s on a local dev box, you can use type NodePort instead
  type: LoadBalancer
  ports:
    - port: 7577
  selector:
    name: skipper
EOF

}

RETRIES=20
WAIT_TIME=15
generate_manifest
kubectl_create
run_scripts "$PWD" "config.sh"
