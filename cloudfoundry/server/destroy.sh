#!/bin/bash

 if (cf apps | grep "scdf-server"); then
    cf delete scdf-server -f -r
 fi
