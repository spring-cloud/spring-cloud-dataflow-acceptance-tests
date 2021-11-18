. $ROOT_DIR/scripts/utility-functions.sh
if [ -z "$SERVER_URI" ] ;
then
DEBUG "Setting server uri"
    export SERVER_URI="https://dataflow-$( cf service $DATAFLOW_SERVICE_INSTANCE_NAME --guid ).$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN"
    wait_for_200 ${SERVER_URI}/about  "Authorization: $(cf oauth-token)"
fi
echo "SCDF SERVER URI: $SERVER_URI"
echo "SCDF SERVER CONFIGURATION:"
wget -qO- --no-check-certificate ${SERVER_URI}/about
