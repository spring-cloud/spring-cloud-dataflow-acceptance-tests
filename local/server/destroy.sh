#!/bin/bash

PID=$(cat app.pid)

if ps -p  $PID > /dev/null; then
  echo "Killing process PID [$PID]" >&2
  kill -9 $PID > /dev/null 2>&1
fi
