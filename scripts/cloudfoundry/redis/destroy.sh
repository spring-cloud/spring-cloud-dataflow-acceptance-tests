#!/bin/sh

if  (cf services | grep "^redis"); then
  cf ds redis -f
fi
