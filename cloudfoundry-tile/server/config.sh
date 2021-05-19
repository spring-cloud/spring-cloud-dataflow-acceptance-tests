#!/usr/bin/env bash


if [ -z "$SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_ID" ]; then
    cf create-service-key $DATAFLOW_SERVICE_INSTANCE_NAME $DATAFLOW_SERVICE_KEY
    echo "getting client credentials for $DATAFLOW_SERVICE_INSTANCE_NAME"
    eval $(python3 scdf_creds.py $DATAFLOW_SERVICE_INSTANCE_NAME $DATAFLOW_SERVICE_KEY)
fi

if [ ! -f "${ROOT_DIR}/$CERT_URI.cer" ]; then
    echo "importing the $CERT_URI certificate to the JDK custom trust store"
    set +e
    openssl s_client -connect  $CERT_URI:443 -showcerts > ${ROOT_DIR}/$CERT_URI.cer </dev/null
    set -e
    JAVA_CACERTS=$JAVA_HOME/jre/lib/security/cacerts
    if [ ! -f $JAVA_CACERTS ]; then
       JAVA_CACERTS=$JAVA_HOME/lib/security/cacerts
    fi
    if [ ! -f  $JAVA_CACERTS ]; then
       echo "ERROR: directory not found: $JAVA_CACERTS."
       exit 1
    fi
    cp $JAVA_CACERTS ${ROOT_DIR}/mycacerts
    $JAVA_HOME/bin/keytool -import -alias myNewCertificate -file "${ROOT_DIR}/$CERT_URI.cer" -noprompt -keystore "${ROOT_DIR}/mycacerts" -storepass changeit
fi
