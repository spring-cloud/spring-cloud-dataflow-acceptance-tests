import json
import sys
import os
import psycopg2
import time
import cx_Oracle
import traceback

def get_traceback(e):
    lines = traceback.format_exception(type(e), e, e.__traceback__)
    return ''.join(lines)

def log(msg):
    #stderr because the config json is piped via stdout. This is out of band
    sys.stderr.write("%s\n" %(msg))

def dbname(db, prefix):
    if db['provider'] == 'oracle':
      return 'xe'
    binder = os.getenv("BINDER", "rabbit")
    db_index = os.getenv("SQL_DB_INDEX" , "")
    return "%s_pro_1_5_0_%s_%s" % (prefix, binder, db_index)

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
         'user': 'SYSTEM',
         'psw': db['password'],
         'service': 'xe',
     }
     conn_str = '{user}/{psw}@{host}:{port}/{service}'.format(**CONN_INFO)
     conn = cx_Oracle.connect(conn_str)
     # todo: set connect_timeout
     with conn.cursor() as cur:
         try:
           cur.execute('ALTER SESSION SET "_ORACLE_SCRIPT"=TRUE')
           cur.execute("SELECT sid,serial# FROM v$session where username='%s'" %(db['username'].upper()))
           for row in cur.fetchall():
               log("killing session " + str(row))
               cur.execute("ALTER SYSTEM kill session '%s,%s' immediate" % (row))
           cur.execute("DROP USER %s CASCADE" % db['username'])
         except cx_Oracle.DatabaseError as e:
            log(get_traceback(e))
         finally:
           cur.execute("CREATE USER %s IDENTIFIED BY %s" % (db['username'], db['password']))
           cur.execute("GRANT ALL PRIVILEGES TO %s" % (db['username']))
           log("completed initialization of DB %s" %(dbname))

   except cx_Oracle.DatabaseError as e:
        # If we fail, continue anyway
        log(get_traceback(e))
   finally:
     if conn:
        conn.close()

def db_config():
    supported_dbs =  ['oracle', 'postgresql']
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

    if not db['provider'] in supported_dbs:
        raise Exception("Invalid db provider '%s' must be one of %s" % (db['provider'], supported_dbs))
    return db

def init_db():
    cx_Oracle.init_oracle_client(lib_dir="/Users/dturanski/Downloads/instantclient_19_8")
    db = db_config()
    if db['provider'] == 'postgresql':
        init_postgres_db(db, dbname('skipper'))
        init_postgres_db(db, dbname('dataflow'))
    elif db['provider'] == 'oracle':
        '''Oracle would need to create different user for each. Using shared DB here'''
        init_oracle_db(db, 'xe')

if __name__ == '__main__':
    init_db()