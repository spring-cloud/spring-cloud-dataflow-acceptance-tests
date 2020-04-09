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
}

function wait_clean_for_label() {
  RETRIES=100
  for i in $( seq 1 "${RETRIES}" ); do
  RESOURCES=$(kubectl get $1 -n $KUBERNETES_NAMESPACE -l $2)
   if [ -z "$RESOURCES" ]; then
     break
   fi
   sleep 10
  done
}
