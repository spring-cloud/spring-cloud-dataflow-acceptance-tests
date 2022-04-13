import json
import sys
import os
import psycopg2
import time
import cx_Oracle
import traceback
sys.path.insert(0, os.getenv("ROOT_DIR") + "/scripts/python")
from config_external_db import db_config, dbname, log

def user_provided_postgresql(db, server):
    name = dbname(db, server)
    user_provided = {}
    port = int(db['port'])
    user_provided['uri'] = "postgresql://%s:%d/%s?user=%s&password=%s" % (db['host'], port, name, db['username'], db['password'])
    user_provided['jdbcUrl'] = "jdbc:%s" % user_provided['uri']
    user_provided['username'] = db['username']
    user_provided['password'] = db['password']
    user_provided['dbname'] = name
    user_provided['host'] = db['host']
    user_provided['port'] = port
    user_provided['tags'] = ['postgres']
    ups = {}
    ups['user-provided'] = user_provided
    return ups

def user_provided_oracle(db, server):
    name = dbname(db, server)
    user_provided = {}
    port = int(db['port'])
    user_provided['uri'] = "oracle:thin://%s:%d/%s?user=%s&password=%s" % (db['host'], port, name, db['username'], db['password'])
    user_provided['jdbcUrl'] = "jdbc:%s" % user_provided['uri']
    user_provided['username'] = db['username']
    user_provided['password'] = db['password']
    user_provided['dbname'] = name
    user_provided['host'] = db['host']
    user_provided['port'] = port
    user_provided['tags'] = ['oracle']
    ups = {}
    ups['user-provided'] = user_provided
    return ups

def user_provided(db, server):
    if db['provider'] == 'postgresql':
       return user_provided_postgresql(db, server)
    elif db['provider'] == 'oracle':
       return user_provided_oracle(db, server)
    else:
        raise Exception("Invalid db provider %s" % db['provider'])

schedules_enabled = os.getenv(
    "SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED", 'false')
dataflow_tile_configuration = os.getenv("DATAFLOW_TILE_CONFIGURATION")
config={}
if dataflow_tile_configuration:
    config = json.loads(dataflow_tile_configuration)

if not config.get('relational-data-service') or not config.get('skipper-relational'):
    db = db_config()

if not config.get('skipper-relational'):
    config['skipper-relational'] = user_provided(db, 'skipper')

if not config.get('relational-data-service'):
    config['relational-data-service'] = user_provided(db, 'dataflow')

if not config.get('maven-cache'):
    config['maven-cache']=True

if schedules_enabled.lower()=='true' and not 'scheduler' in config.keys():
    schedules_service_name = os.environ['SCHEDULES_SERVICE_NAME']
    schedules_plan_name = os.environ['SCHEDULES_PLAN_NAME']
    config['scheduler'] = {'name': schedules_service_name, 'plan': schedules_plan_name}
if config:
    print(json.dumps(config))
