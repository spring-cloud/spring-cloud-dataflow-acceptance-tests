#!/bin/bash

 if (cf apps | grep "skipper-server"); then
    SKIPPER_NAME=$(cf apps | grep skipper-server- | awk '{print $1}' | sed 's:,::g')
    for server in $SKIPPER_NAME
    do
      if [[ $server != 'No' ]]; then
        echo "Deleting the app: $server"
        cf delete $server -f -r
      fi
    done
 fi

 apps=`cf apps | awk 'FNR > 3 {print $1}'`
 for app in $apps
 do
    if [[ $app != 'No' ]]; then
        echo "Deleting the app: $app"
        cf delete $app -f -r
    fi
 done

