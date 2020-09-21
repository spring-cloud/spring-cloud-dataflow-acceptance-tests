function distro_files_object_delete() {
  kubectl delete all -l app=rabbitmq --namespace $KUBERNETES_NAMESPACE --wait || true
  kubectl delete all,pvc,secrets -l app=mysql --namespace $KUBERNETES_NAMESPACE --wait || true
  kubectl delete all,cm -l app=skipper --namespace $KUBERNETES_NAMESPACE --wait || true
  kubectl delete all -l app=kafka --namespace $KUBERNETES_NAMESPACE --wait || true
  kubectl delete all,cm -l app=scdf-server --namespace $KUBERNETES_NAMESPACE --wait || true
  kubectl delete role scdf-role --namespace $KUBERNETES_NAMESPACE --wait || true
  kubectl delete rolebinding scdf-rb --namespace $KUBERNETES_NAMESPACE --wait || true
  kubectl delete serviceaccount $DATAFLOW_SERVICE_ACCOUNT_NAME --namespace $KUBERNETES_NAMESPACE --wait || true
  # Clean up any stray apps
  kubectl delete all -l role=spring-app --namespace $KUBERNETES_NAMESPACE
}

function helm_delete() {
  helm delete scdf --purge || true
  wait_clean_for_label "all" "release=scdf"

  # Clean up any stray apps
  kubectl delete all -l role=spring-app --namespace $KUBERNETES_NAMESPACE

  kubectl delete pvc -l app.kubernetes.io/instance=scdf --namespace $KUBERNETES_NAMESPACE
  wait_clean_for_label "pvc" "app.kubernetes.io/instance=scdf"

  kubectl delete pvc -l app=mariadb --namespace $KUBERNETES_NAMESPACE
  wait_clean_for_label "pvc" "app=mariadb"

  kubectl delete pvc -l app=rabbitmq --namespace $KUBERNETES_NAMESPACE
  wait_clean_for_label "pvc" "app=rabbitmq"
}

function wait_clean_for_label() {
  RETRIES=100
  echo "Waiting on resource: $1 with label: $2 to delete"	
  for i in $( seq 1 "${RETRIES}" ); do
  RESOURCES=$(kubectl get $1 -n $KUBERNETES_NAMESPACE -l $2)
   if [ -z "$RESOURCES" ]; then
     break
   fi
   sleep 10
  done
}
