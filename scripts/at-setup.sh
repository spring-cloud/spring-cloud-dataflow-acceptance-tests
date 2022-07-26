#!/bin/bash
# default exit code
stat=0
echo "SETUP_TOOL_REPO=$SETUP_TOOL_REPO"
echo "SQL_DATAFLOW_DB_NAME=$SQL_DATAFLOW_DB_NAME"
echo "SQL_SKIPPER_DB_NAME=$SQL_SKIPPER_DB_NAME"

python3 -m pip install --upgrade pip | grep -v 'Requirement already satisfied'
pip3 install -r $SETUP_TOOL_REPO/requirements.txt | grep -v 'Requirement already satisfied'

load_file() {
  filename=$1
  echo "exporting required env variables from $filename :"
  while IFS='=' read -r var value; do
  if ! [[ $var == \#* ]]; then
    # only the un-set variables are exported
    if [ -z ${!var} ]; then
      export $var="$(eval echo $value)"
    fi
  fi
  done < "$filename"
}

ARGS=$@
#This consumes $@ so save to ARGS first
while [[ $# > 0 && -z $SQL_PROVIDER ]]
do
  key="$1"
  if [[ $key = "--sqlProvider" ]]; then
    SQL_PROVIDER="$2"
  fi
  shift
done

if [[ ! -z "$SQL_PROVIDER" ]]; then
  echo "SQL_PROVIDER = $SQL_PROVIDER"
fi

os=$(uname)
if [[ "$os" == "Linux" ]]; then
    if ! command -v cf &> /dev/null
    then
      echo "Installing CloudFoundry CLI"
      wget -q -O - https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | sudo apt-key add -
      echo "deb https://packages.cloudfoundry.org/debian stable main" | sudo tee /etc/apt/sources.list.d/cloudfoundry-cli.list
      sudo apt-get update
      sudo apt-get install cf-cli
    fi
    if [[ "$SQL_PROVIDER" == "oracle" ]]; then
      echo "Installing ORACLE components"
      wget -q https://download.oracle.com/otn_software/linux/instantclient/215000/instantclient-basiclite-linux.x64-21.5.0.0.0dbru.zip
      unzip instantclient-basiclite-linux.x64-21.5.0.0.0dbru.zip
      export LD_LIBRARY_PATH=$PWD/instantclient_21_5
    fi
elif [[ "$os" == "Darwin" ]]; then
  if [[ "$SQL_PROVIDER" == "oracle" ]] ; then
    if [[ ! -d "./instantclient_19_8" ]]; then
      echo "Installing ORACLE components"
      wget -q https://download.oracle.com/otn_software/mac/instantclient/198000/instantclient-basiclite-macos.x64-19.8.0.0.0dbru.zip
      unzip instantclient-basiclite-macos.x64-19.8.0.0.0dbru.zip
    fi
    export LD_LIBRARY_PATH=$PWD/instantclient_19_8
  fi
fi

pushd $SETUP_TOOL_REPO  > /dev/null
  export PYTHONPATH=./src:$PYTHONPATH
  echo "PYTHONPATH=$PYTHONPATH" >> $GITHUB_ENV
  python3 -m install.clean -v
  RC=$?
  if [[ $RC -gt 0 ]]; then
    exit 1
  fi

  python3 -m install.setup -v --initializeDB
  RC=$?
  if [[ $RC -gt 0 ]]; then
    exit 1
  fi
  load_file "cf_scdf.properties"
  echo "Dataflow Server is live @ $SPRING_CLOUD_DATAFLOW_CLIENT_SERVER_URI"
popd > /dev/null
