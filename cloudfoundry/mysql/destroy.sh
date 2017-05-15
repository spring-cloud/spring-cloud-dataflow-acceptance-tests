#!/bin/bash

if (cf services | grep "^mysql"); then
  cf ds mysql -f
fi
