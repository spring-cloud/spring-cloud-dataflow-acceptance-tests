#
# Run AT Tests. Required environment variables must be set.
#
#Install required POSTGRESQL lib for python
PATH=$PATH:~/.local/bin
python3 -m pip install --upgrade pip
pip3 install psycopg2-binary

#Install cf cli
if ! command -v cf &> /dev/null
then
  wget -q -O - https://packages.cloudfoundry.org/debian/cli.cloudfoundry.org.key | sudo apt-key add -
  echo "deb https://packages.cloudfoundry.org/debian stable main" | sudo tee /etc/apt/sources.list.d/cloudfoundry-cli.list
  sudo apt-get update
  sudo apt-get install cf-cli
fi

SCHED_FLAG=""
if [ "$SCHEDULER" == "true" ]; then
        SCHED_FLAG="-se"
fi

echo CLEANING UP RESOURCES BEFORE RUNNING TESTS
./run.sh clean -se
echo FINISHED CLEANING UP RESOURCES
# setup environment
./run.sh setup -cc $SCHED_FLAG
# Run the tests
echo RUNNING TESTS
# ensure no false prometheus is detected.
export TEST_PLATFORM_CONNECTION_PROMETHEUS_URL=none
export MAVEN_PROPERTIES="-Dtest.docker.compose.disable.extension=true -Djavax.net.ssl.trustStore=${PWD}/mycacerts -Djavax.net.ssl.trustStorePassword=changeit"
./run.sh tests -c -cc $SCHED_FLAG --tests !DataFlowAT#streamAppCrossVersion,!DataFlowAT#streamPartitioning,!BatchRemotePartitioningAT#runBatchRemotePartitionJobCloudFoundry
status=$?
echo FINISHED RUNNING TESTS
./run.sh clean $SCHED_FLAG
exit $status
