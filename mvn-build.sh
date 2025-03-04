#!/usr/bin/env bash
if [ "$ARTIFACTORY_USERNAME" = "" ] || [ "$ARTIFACTORY_PASSWORD" = "" ]; then
  echo "Environmental variables named ARTIFACTORY_USERNAME and ARTIFACTORY_PASSWORD must be present"
  exit 1
fi
./mvnw -s .settings.xml $*
