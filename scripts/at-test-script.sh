#!/bin/bash
#Install required libs for python
#PATH=$PATH:~/.local/bin
SETUP_TOOL_REPO="scdf_cf_setup"
echo "SETUP_TOOL_REPO=$SETUP_TOOL_REPO"
echo "SQL_DATAFLOW_DB_NAME=$SQL_DATAFLOW_DB_NAME"
echo "SQL_SKIPPER_DB_NAME=$SQL_SKIPPER_DB_NAME"
if [[ -d $SETUP_TOOL_REPO ]]; then
  rmdir -f $SETUP_TOOL_REPO
fi
git clone https://github.com/dturanski/scdf_cf_setup.git
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

function run_tests() {
  HTTPS_ENABLED="true"
  if [[ -z "$SPRING_CLOUD_STREAM_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION"]]; then
    SPRING_CLOUD_STREAM_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION="false"
  fi
# Add -Dmaven.surefire.debug to enable remote debugging on port 5005.
#
eval "./mvnw -U -B -Dspring.profiles.active=blah -Dtest=$TESTS -DPLATFORM_TYPE=$PLATFORM\\
  -DSKIP_CLOUD_CONFIG=true -Dtest.docker.compose.disable.extension=true -Dspring.cloud.dataflow.client.serverUri=$SERVER_URI \\
  -Dspring.cloud.dataflow.client.skipSslValidation=$SPRING_CLOUD_STREAM_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION \\
  -Dtest.platform.connection.platformName=$PLATFORM_NAME \\
  -Dtest.platform.connection.applicationOverHttps=$HTTPS_ENABLED \\
  $MAVEN_PROPERTIES clean test surefire-report:report"
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

pushd $SETUP_TOOL_REPO
export PYTHONPATH=./src:$PYTHONPATH
echo $PWD
python3 -m install.clean -v --appsOnly
if [[ $? > 0 ]]; then
  exit 1
fi

python3 -m install.setup -v
if [[ $? > 0 ]]; then
  exit 1
fi
load_file "cf_scdf.properties"
echo "SERVER_URI=$SERVER_URI"
popd
echo "Running Tests..."
run_tests