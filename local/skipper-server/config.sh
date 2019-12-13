#!/usr/bin/env bash

APP_LOG_PATH=$PWD/app-logs
export APPLICATION_ARGS="$APPLICATION_ARGS --spring.cloud.skipper.server.platform.local.accounts.default.workingDirectoriesRoot=$APP_LOG_PATH"

