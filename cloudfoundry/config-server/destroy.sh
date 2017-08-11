#!/bin/bash

echo "Cleaning up config server service"

if (cf services | grep "^cloud-config-server"); then
  cf ds cloud-config-server -f
fi
