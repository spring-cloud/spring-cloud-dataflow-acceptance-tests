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
# TODO determine which are required and set environment variables. in .github/workflows/at-common-workflow.yml:126
  # need to find value for spring.cloud.dataflow.client.authentication.access-token
  # spring.cloud.dataflow.client.authentication.token-uri
  # spring.cloud.dataflow.client.authentication.oauth2.clientRegistrationId
  # spring.cloud.dataflow.client.authentication.oauth2.username
  # spring.cloud.dataflow.client.authentication.oauth2.password;
  # env SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_ACCESS_TOKEN
./mvnw -U -B -Dspring.profiles.active=blah -Dtest=$TESTS \
  -DSKIP_CLOUD_CONFIG=true -Dtest.docker.compose.disable.extension=true -Dspring.cloud.dataflow.client.serverUri=$SERVER_URI \
  -Dspring.cloud.dataflow.client.skipSslValidation=$SKIP_SSL_VALIDATION -Dtest.platform.connection.platformName=default \
  -Dtest.platform.connection.applicationOverHttps=$HTTPS_ENABLED \
  -Dmaven-failsafe-plugin.groups=all,group3 \
  $MAVEN_PROPERTIES clean verify surefire-report:failsafe-report-only
