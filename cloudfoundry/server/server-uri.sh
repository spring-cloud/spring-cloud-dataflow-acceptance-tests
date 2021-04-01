. $ROOT_DIR/scripts/utility-functions.sh
if [ -z "$SERVER_URI" ] ;
then
    SERVER_URI=$(cf apps | grep dataflow-server- | awk '{print $6}' | sed 's:,::g')
    export SERVER_URI="https://$SERVER_URI"
    wait_for_200 ${SERVER_URI}/about
fi
echo "SCDF SERVER URI: $SERVER_URI"
