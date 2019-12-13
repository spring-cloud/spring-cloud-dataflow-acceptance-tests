#!/usr/bin/env bash

 if (cf apps | grep "scdf-server"); then
    SCDF_NAME=$(cf apps | grep dataflow-server- | awk '{print $1}' | sed 's:,::g')
    for server in $SCDF_NAME
    do
       if [[ $server != 'No' ]]; then
          echo "Deleting the app: $server"
          cf delete $server -f -r || exit 0
       fi
    done
 fi

 apps=`cf apps | awk 'FNR > 3 {print $1}'`
 for app in $apps
 do
     if [[ $app != 'No' && $app != 'name' ]]; then
        echo "Deleting the app: $app"
        cf delete $app -f -r || exit 0
    fi
 done
