#!/bin/bash
export TEST_PLATFORM_CONNECTION_PROMETHEUS_URL=none
JAVA_TRUST_STORE=${PWD}/scdf_cf_setup/mycacerts
MAVEN_PROPERTIES="-Dtest.docker.compose.disable.extension=true -Djavax.net.ssl.trustStore=${JAVA_TRUST_STORE} -Djavax.net.ssl.trustStorePassword=changeit"
TESTS="!DataFlowAT#streamAppCrossVersion,!DataFlowAT#streamPartitioning,!BatchRemotePartitioningAT#runBatchRemotePartitionJobCloudFoundry"
HTTPS_ENABLED="true"
SERVER_URI="$SPRING_CLOUD_DATAFLOW_CLIENT_SERVER_URI"
SKIP_SSL_VALIDATION="$SPRING_CLOUD_STREAM_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION"
if [[ -z "$SPRING_CLOUD_STREAM_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION" ]]; then
  SKIP_SSL_VALIDATION="false"
fi

./mvnw -U -B -Dspring.profiles.active=blah -Dtest=$TESTS \
  -DSKIP_CLOUD_CONFIG=true -Dtest.docker.compose.disable.extension=true -Dspring.cloud.dataflow.client.serverUri=$SERVER_URI \
  -Dspring.cloud.dataflow.client.skipSslValidation=$SKIP_SSL_VALIDATION -Dtest.platform.connection.platformName=default \
  -Dtest.platform.connection.applicationOverHttps=$HTTPS_ENABLED \
  $MAVEN_PROPERTIES clean verify surefire-report:failsafe-report-only
