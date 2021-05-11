import json
import sys
import os

schedules_enabled = os.getenv(
    "SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED", 'false')
dataflow_tile_configuration = os.getenv("DATAFLOW_TILE_CONFIGURATION", "")
config={}
if len(dataflow_tile_configuration):
    config = json.loads(dataflow_tile_configuration)

config['maven-cache']=True

if schedules_enabled.lower()=='true' and not 'scheduler' in config.keys():
    schedules_service_name = os.environ['SCHEDULES_SERVICE_NAME']
    schedules_plan_name = os.environ['SCHEDULES_PLAN_NAME']
    config['scheduler'] = {'name': schedules_service_name, 'plan': schedules_plan_name}

if config:
    print(json.dumps(config))


