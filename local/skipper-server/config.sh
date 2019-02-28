#!/usr/bin/env bash

APP_LOG_PATH=$PWD/app-logs
export APPLICATION_ARGS="$APPLICATION_ARGS --spring.cloud.skipper.server.platform.local.accounts.default.workingDirectoriesRoot=$APP_LOG_PATH"
SKIPPER_SERVER_URI="http://localhost:7577"
echo "SKIPPER SERVER URI: $SKIPPER_SERVER_URI"
export SKIPPER_SERVER_URI
