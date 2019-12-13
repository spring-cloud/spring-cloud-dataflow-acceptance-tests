if [ -z "$SERVER_URI" ] ;
then
  export SERVER_URI="http://localhost:9393"
  wait_for_200 ${SERVER_URI}/about
fi
echo "SCDF SERVER URI: $SERVER_URI"
