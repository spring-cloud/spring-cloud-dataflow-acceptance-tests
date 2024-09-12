#!/bin/bash
# default exit code
stat=0
echo "SETUP_TOOL_REPO=$SETUP_TOOL_REPO"
echo "SQL_DATAFLOW_DB_NAME=$SQL_DATAFLOW_DB_NAME"
echo "SQL_SKIPPER_DB_NAME=$SQL_SKIPPER_DB_NAME"
# override completely by providing a correct value
if [ "$BINDER" = "rabbit" ]; then
  export BROKER=rabbitmq
else
  export BROKER=$BINDER
fi
if [ "$BROKER" = "rabbitmq" ]; then
  export BROKERNAME=rabbit
else
  export BROKERNAME=$BROKER
fi
if [ "$BROKERNAME" = "" ]; then
  echo "Error expected BROKERNAME from $BROKER" >&2
  exit 2
fi

export STREAM_APPS_URI=
# change to RELEASE to use the latest version or any specific version
STREAM_APPS_VERSION=2021.1.2
# will default if blank
REPO=
if [[ "$STREAM_APPS_VERSION" = *"SNAPSHOT"* ]]; then
  REPO=https://repo.spring.io/artifactory/snapshot
elif [[ "$STREAM_APPS_VERSION" = "RELEASE" ]]; then
  REPO=
else
  REPO=https://repo1.maven.org/maven2
fi
if [[ "$STREAM_APPS_URI" = "" ]]; then
  if [[ "$REPO" != "" ]]; then
    export STREAM_APPS_URI="$REPO/org/springframework/cloud/stream/app/stream-applications-descriptor/$STREAM_APPS_VERSION/stream-applications-descriptor-$STREAM_APPS_VERSION.stream-apps-$BROKERNAME-maven"
  else
    export STREAM_APPS_URI=https://dataflow.spring.io/$BROKER-maven-latest
  fi
fi
echo "Registering $STREAM_APPS_URI"

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
  done <"$filename"
}

ARGS=$@
#This consumes $@ so save to ARGS first
while [[ $# > 0 && -z $SQL_PROVIDER ]]; do
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
if [[ "$os" = "Linux" ]]; then
  if ! command -v cf &>/dev/null; then
    echo "Installing CloudFoundry CLI"
    export APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=true
    wget -q -O - https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | sudo apt-key add -
    echo "deb https://packages.cloudfoundry.org/debian stable main" | sudo tee /etc/apt/sources.list.d/cloudfoundry-cli.list
    sudo apt-get update
    sudo apt-get install cf-cli
  fi
  if [[ "$SQL_PROVIDER" = "oracle" ]]; then
    echo "Installing ORACLE components"
    wget -q https://download.oracle.com/otn_software/linux/instantclient/215000/instantclient-basiclite-linux.x64-21.5.0.0.0dbru.zip
    unzip instantclient-basiclite-linux.x64-21.5.0.0.0dbru.zip
    export LD_LIBRARY_PATH=$PWD/instantclient_21_5
  fi
elif [[ "$os" = "Darwin" ]]; then
  if [[ "$SQL_PROVIDER" = "oracle" ]]; then
    if [[ ! -d "./instantclient_19_8" ]]; then
      echo "Installing ORACLE components"
      wget -q https://download.oracle.com/otn_software/mac/instantclient/198000/instantclient-basiclite-macos.x64-19.8.0.0.0dbru.zip
      unzip instantclient-basiclite-macos.x64-19.8.0.0.0dbru.zip
    fi
    export LD_LIBRARY_PATH=$PWD/instantclient_19_8
  fi
fi

pushd $SETUP_TOOL_REPO >/dev/null
export PYTHONPATH=./src:$PYTHONPATH
echo "PYTHONPATH=$PYTHONPATH" >>$GITHUB_ENV
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
if [ "$SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_ACCESS_TOKEN" != "" ]; then
  echo "SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_ACCESS_TOKEN present"
else
  echo "SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_ACCESS_TOKEN blank"
fi
if [ "$SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_TOKEN_URI" != "" ]; then
  echo "SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_TOKEN_URI present"
else
  echo "SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_TOKEN_URI present"
fi
if [ "$SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_ID" != "" ]; then
  echo "SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_ID present"
else
  echo "SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_ID present"
fi
if [ "$SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_SECRET" != "" ]; then
  echo "SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_SECRET present"
else
  echo "SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_SECRET present"
fi

popd >/dev/null
