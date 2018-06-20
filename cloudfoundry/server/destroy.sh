#!/bin/bash

 if (cf apps | grep "scdf-server"); then
    SCDF_NAME=$(cf apps | grep dataflow-server- | awk '{print $1}' | sed 's:,::g')
    if [[ ! -z $SCDF_NAME ]]; then
      echo "Deleting: $SCDF_NAME"
      cf delete $SCDF_NAME -f -r
    fi
 fi

 apps=`cf apps | awk 'FNR > 3 {print $1}'`
 for app in $apps
 do
    if [[ $app != 'No' ]]; then
        echo "Deleting the app: $app"
        cf delete $app -f -r
    fi
 done

