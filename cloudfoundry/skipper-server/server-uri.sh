if [ -z "$SKIPPER_SERVER_URI" ] ;
then
   SKIPPER_SERVER_URI=$(cf apps | grep skipper-server- | awk '{print $6}' | sed 's:,::g')
   echo "exporting SKIPPER SERVER URI as $SKIPPER_SERVER_URI"
   export SKIPPER_SERVER_URI="https://$SKIPPER_SERVER_URI"
   wait_for_200 ${SKIPPER_SERVER_URI}/api/about
else
  echo "SKIPPER SERVER URI is already set to $SKIPPER_SERVER_URI"
fi
echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
