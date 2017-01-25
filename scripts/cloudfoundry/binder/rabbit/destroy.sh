#!/bin/sh

if (cf services | grep "^rabbit"); then
  cf ds rabbit -f
fi
