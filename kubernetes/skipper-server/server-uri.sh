if [ -z "$SKIPPER_SERVER_URI" ] ;
then
  SKIPPER_IP=$(kubectl get svc --namespace $KUBERNETES_NAMESPACE | grep skipper | awk '{print $4}')
  if [[ "$SKIPPER_IP" =~ [\d{1-3}:\d{1-3}:\d{1-3}:\d{1-3}] ]] ;
  then
       export SKIPPER_SERVER_URI="http://$SKIPPER_IP"
       wait_for_200 ${SKIPPER_SERVER_URI}/api/about
  else
      echo "Unable to determine skipper server host $SKIPPER_IP Did deployment fail?"
      exit 1
  fi
fi
echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
