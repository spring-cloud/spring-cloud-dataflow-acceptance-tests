#!/usr/bin/env bash
if [ "$SCDF_PRO_VERSION" == "" ]
then
  SCDF_PRO_VERSION=1.5.0-SNAPSHOT
fi
docker pull "pivotal/scdf-pro-server:$SCDF_PRO_VERSION"
