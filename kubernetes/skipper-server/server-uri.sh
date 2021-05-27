if [ -z "$SKIPPER_SERVER_URI" ] ;
then
  SKIPPER_IP=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
  while [[ "$SKIPPER_IP" == "<pending>" ]] ; do
    sleep 1
    SKIPPER_IP=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
  done

  if [ "$HTTPS_ENABLED" == "true" ]; then
    export SKIPPER_SERVER_URI="https://$SKIPPER_IP"
  else
    export SKIPPER_SERVER_URI="http://$SKIPPER_IP"
  fi

  wait_for_200 ${SKIPPER_SERVER_URI}/api/about
fi
