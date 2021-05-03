if [ -z "$SERVER_URI" ] ;
then
  SCDF_IP=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep server | awk '{print $4}')
  while [[ "$SCDF_IP" == "<pending>" ]] ; do
    sleep 1
    SCDF_IP=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep server | awk '{print $4}')
  done

  if [ "$HTTPS_ENABLED" ]; then
    export SERVER_URI="https://$SCDF_IP"
  else
    export SERVER_URI="http://$SCDF_IP"
  fi

  DEBUG "executing wait_for_200 on $SERVER_URI"
  wait_for_200 ${SERVER_URI}/about
fi
echo "SCDF SERVER URI: $SERVER_URI"
