if [ -z "$SKIPPER_SERVER_URI" ] ;
then
  export SKIPPER_SERVER_URI="http://localhost:7577"
  wait_for_200 ${SKIPPER_SERVER_URI}/api/about
fi
echo "SKIPPER_SERVER_URI  URI: $SKIPPER_SERVER_URI"
