if [ -z "$SKIPPER_SERVER_URI" ] ;
then
   SKIPPER_SERVER_URI=$(cf apps | grep skipper-server- | awk '{print $6}' | sed 's:,::g')
   export SKIPPER_SERVER_URI="https://$SKIPPER_SERVER_URI"
   wait_for_200 ${SKIPPER_SERVER_URI}/api/about
fi
echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
