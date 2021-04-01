#!/usr/bin/env bash

if [ -f 'app.pid' ]; then

  PID=$(cat app.pid)
  if ps -p $PID > /dev/null; then
    echo "Killing process PID [$PID]" >&2
    kill $PID > /dev/null 2>&1
  fi
fi
