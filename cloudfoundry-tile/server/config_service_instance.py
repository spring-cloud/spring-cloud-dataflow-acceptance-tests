import json
import sys
import os
import psycopg2
import time
import cx_Oracle

def log(msg):
    #stderr because the config json is piped via stdout. This is out of band
    sys.stderr.write("%s\n" %(msg))

def dbname(prefix):
    binder = os.getenv("BINDER", "rabbit")
    db_index = os.getenv("SQL_DB_INDEX" , "")
    return "%s_pro_1_5_0_%s_%s" % (prefix, binder, db_index)


def user_provided_postgresql(db, dbname):
    user_provided = {}
    port = int(db['port'])
    user_provided['uri'] = "postgresql://%s:%d/%s?user=%s&password=%s" % (db['host'], port, dbname, db['username'], db['password'])
    user_provided['jdbcUrl'] = "jdbc:%s" % user_provided['uri']
    user_provided['username'] = db['username']
    user_provided['password'] = db['password']
    user_provided['dbname'] = dbname
    user_provided['host'] = db['host']
    user_provided['port'] = port
    user_provided['tags'] = ['postgres']
    ups = {}
    ups['user-provided'] = user_provided
    return ups

def user_provided_oracle(db, dbname):
    user_provided = {}
    port = int(db['port'])
    user_provided['uri'] = "oracle:thin://%s:%d/%s?user=%s&password=%s" % (db['host'], port, dbname, db['username'], db['password'])
    user_provided['jdbcUrl'] = "jdbc:%s" % user_provided['uri']
    user_provided['username'] = db['username']
    user_provided['password'] = db['password']
    user_provided['dbname'] = dbname
    user_provided['host'] = db['host']
    user_provided['port'] = port
    user_provided['tags'] = ['oracle']
    ups = {}
    ups['user-provided'] = user_provided
    return ups

def user_provided(db, dbname):
    if db['provider'] == 'postgresql':
       return user_provided_postgresql(db, dbname)
    elif db['provider'] == 'oracle':
       return user_provided_oracle(db, dbname)
    else:
        raise Exception("Invalid db provider %s" % db['provider'])

def init_postgres_db(db, dbname):
  #stderr because the config json is piped via stdout.
  log("initializing DB %s..." %(dbname))
  conn = None
  try:
      # Connect to the postgres (admin) database to drop/create target database
      conn = psycopg2.connect(
            host = db['host'],
            port = db['port'],
            user = db['username'],
            password = db['password'],
            dbname = 'postgres',
            connect_timeout = 5)
      conn.autocommit = True
      with conn.cursor() as cur:
        cur.execute("SELECT count(*) FROM pg_stat_activity WHERE datname = %s;",(dbname,))
        live_connections = cur.fetchone()[0]
        log("DB %s has %d live connections" %(dbname, live_connections))
        while live_connections > 0:
            # Terminate any existing connections to the database
            cur.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = %s;",(dbname,))
            time.sleep(1.0)
            cur.execute("SELECT count(*) FROM pg_stat_activity WHERE datname = %s;",(dbname,))
            live_connections = cur.fetchone()[0]
            log("DB %s has %d live connections" %(dbname, live_connections))

        cur.execute("DROP DATABASE IF EXISTS %s;" % dbname)
        cur.execute("CREATE DATABASE %s;" % dbname)
        log("completed initialization of DB %s" %(dbname))
  except (psycopg2.DatabaseError, psycopg2.OperationalError) as e:
     # If we fail, continue anyway
     log(f'Error {e}')
  finally:
     if conn:
        conn.close()

def init_oracle_db(db, dbname):
   log("initializing DB %s..." %(dbname))
   conn = None
   try:
     CONN_INFO = {
         'host': db['host'],
         'port': db['port'],
         'user': db['username'],
         'psw': db['password'],
         'service': 'xe',
     }
     log(CONN_INFO)
     conn_str = '{user}/{psw}@{host}:{port}/{service}'.format(**CONN_INFO)
     log(conn_str)
     conn = cx_Oracle.connect(conn_str)
     # todo: set connect_timeout
     with conn.cursor() as cur:
         #cur.execute("DROP DATABASE IF EXISTS %s;" % dbname)
         #cur.execute("CREATE DATABASE %s;" % dbname)
         log("completed initialization of DB %s" %(dbname))

   except cx_Oracle.DatabaseError as e:
        # If we fail, continue anyway
        log(f'Error {e}')
   finally:
     if conn:
        conn.close()

def init_db(db, dbname):
   if db['provider'] == 'postgresql':
      init_postgres_db(db, dbname)
   elif db['provider'] == 'oracle':
      init_oracle_db(db, dbname)
   else:
     raise Exception("Invalid db provider %s" % db['provider'])


schedules_enabled = os.getenv(
    "SPRING_CLOUD_DATAFLOW_FEATURES_SCHEDULES_ENABLED", 'false')
dataflow_tile_configuration = os.getenv("DATAFLOW_TILE_CONFIGURATION")
config={}
if dataflow_tile_configuration:
    config = json.loads(dataflow_tile_configuration)

if not config.get('relational-data-service') or not config.get('skipper-relational'):
    db = {}
    db['username'] = os.getenv("SQL_USERNAME")
    db['password'] = os.getenv("SQL_PASSWORD")
    db['host'] = os.getenv("SQL_HOST")
    db['port'] = os.getenv("SQL_PORT", "5432")
    db['provider'] = os.getenv("SQL_PROVIDER","postgresql")
    if not db['password']:
        sys.exit("FATAL: 'SQL_PASSWORD' is not set")
    if not db['host']:
        sys.exit("FATAL: 'SQL_HOST' is not set")

if not config.get('skipper-relational'):
    config['skipper-relational'] = user_provided(db, dbname('skipper'))
    init_db(db, dbname('skipper'))

if not config.get('relational-data-service'):
    config['relational-data-service'] = user_provided(db, dbname('dataflow'))
    init_db(db, dbname('dataflow'))

if not config.get('maven-cache'):
    config['maven-cache']=True

if schedules_enabled.lower()=='true' and not 'scheduler' in config.keys():
    schedules_service_name = os.environ['SCHEDULES_SERVICE_NAME']
    schedules_plan_name = os.environ['SCHEDULES_PLAN_NAME']
    config['scheduler'] = {'name': schedules_service_name, 'plan': schedules_plan_name}

if config:
    print(json.dumps(config))
