if [ -z "$SERVER_URI" ] ;
then
    SCDF_IP=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep server | awk '{print $4}')
    if [[ "$SCDF_IP" =~ [\d{1-3}:\d{1-3}:\d{1-3}:\d{1-3}] ]] ;
    then
      export SERVER_URI="http://$SCDF_IP"
      DEBUG "executing wait_for_200 on $SERVER_URI"
      wait_for_200 ${SERVER_URI}/about
    else
      echo "Unable to determine server host. Did deployment fail?"
      exit 1
    fi
fi
echo "SCDF SERVER URI: $SERVER_URI"
