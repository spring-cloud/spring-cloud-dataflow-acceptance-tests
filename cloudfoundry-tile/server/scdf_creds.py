import json
import sys
import subprocess
import re
import os

if len(sys.argv) != 3:
    print("Usage: scdf_creds <dataflow_service_instance_name> <service_key")
    exit(1)


dataflow_service_instance = sys.argv[1]
service_key = sys.argv[2]
get_service_key = "cf service-key %s %s" % (dataflow_service_instance, service_key)
out = subprocess.getoutput(get_service_key)
if 'FAILED' in out:
    print(out)
    exit(1)

creds=re.sub("Getting key.+\n","", out)
doc = json.loads(creds)

print("export SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_TOKEN_URI=%s; " % doc['access-token-url'])
print("export SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_ID=%s; " % doc['client-id'])
print("export SPRING_CLOUD_DATAFLOW_CLIENT_AUTHENTICATION_CLIENT_SECRET=%s; " % doc['client-secret'])
print("export SPRING_CLOUD_DATAFLOW_CLIENT_SERVER_URI=%s" % doc['dataflow-url'])

