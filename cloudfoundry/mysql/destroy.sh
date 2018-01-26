#!/bin/bash

if (cf services | grep "^mysql"); then
  cf ds mysql -f
fi

if (cf services | grep "^mysql_skipper"); then
  cf ds mysql_skipper -f
fi
