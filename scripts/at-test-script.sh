#
# Run AT Tests. Required environment variables must be set.
#
#Install required POSTGRESQL lib for python
curl -L get-pip.io | python3
pip install psycopg2-binary

echo CLEANING UP RESOURCES BEFORE RUNNING TESTS
./run.sh clean -se
echo FINISHED CLEANING UP RESOURCES
# setup environment
./run.sh setup -cc -se
# Run the tests
echo RUNNING TESTS
# ensure no false prometheus is detected.
export TEST_PLATFORM_CONNECTION_PROMETHEUS_URL=none
export MAVEN_PROPERTIES="-Dtest.docker.compose.disable.extension=true -Djavax.net.ssl.trustStore=${PWD}/mycacerts -Djavax.net.ssl.trustStorePassword=changeit"
./run.sh tests -c -cc -se --tests !DataFlowAT#streamAppCrossVersion,!DataFlowAT#streamPartitioning,!BatchRemotePartitioningAT#runBatchRemotePartitionJobCloudFoundry
echo FINISHED RUNNING TESTS
./run.sh clean -se
