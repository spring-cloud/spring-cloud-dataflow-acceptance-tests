#!/usr/bin/env bash

set -o errexit

# ======================================= FUNCTIONS START =======================================



function cf_authenticate_and_target() {
  echo "api is : $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL"

  if [ -z "JAVA_BUILDPACK" ]; then
    export JAVA_BUILDPACK='java_buildpack'
  fi
  echo "buildpack is : $JAVA_BUILDPACK"

  [ -z "$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL" ] && { echo "Environment variable SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL must be set"; exit 1; }
  [ -z "$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN" ] && { echo "Environment variable SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN must be set"; exit 1; }
  [ -z "$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME" ] && { echo "Environment variable SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME must be set"; exit 1; }
  [ -z "$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD" ] && { echo "Environment variable SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD must be set"; exit 1; }
  # SKIP_CERT_VERIFY = true if you are using self signed certs

  if [ "${SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION}" == "true" ]; then
    cf_skip_ssl_validation="--skip-ssl-validation"
  else
    cf_skip_ssl_validation=""
  fi
  cf api $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL $cf_skip_ssl_validation
  cf auth $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD
}

function cf_create_broker_org_space() {
  [ -z "$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG" ] && { echo "Environment variable SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG must be set"; exit 1; }
  [ -z "$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE" ] && { echo "Environment variable SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE must be set"; exit 1; }

  if ! (cf orgs | grep "^${SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG}$"); then
    cf create-org $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG
  fi
  cf target -o $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG

  if ! (cf spaces | grep "^${SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE}$"); then
    cf create-space $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE
  fi

}



function cf_target_broker_org_space() {
  [ -z "$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG" ] && { echo "Environment variable SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG must be set"; exit 1; }
  [ -z "$SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE" ] && { echo "Environment variable SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE must be set"; exit 1; }

  cf target -o $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG -s $SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE
}

# ======================================= FUNCTIONS END =======================================



echo "Setting the environment variables"
load_file "$PWD/env.properties"

if ! command_exists cf; then
  echo "You don't have a Cloudfoundry command line executable please visit https://github.com/cloudfoundry/cli to download it first"
  exit 1
fi

cf_authenticate_and_target
cf_create_broker_org_space
cf_target_broker_org_space


