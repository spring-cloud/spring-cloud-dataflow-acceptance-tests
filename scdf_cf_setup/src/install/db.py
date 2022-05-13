__copyright__ = '''
Copyright 2022 the original author or authors.
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
'''

__author__ = 'David Turanski'

import os
import psycopg2
import time
import cx_Oracle
import logging

from cloudfoundry.platform.config.db import DatasourceConfig

logger = logging.getLogger(__name__)


def init_postgres_db(db_config, dbname):
    logger.info("initializing postgresql DB %s..." % dbname)
    conn = None
    try:
        # Connect to the postgres (system) database to drop/create target schema
        conn = psycopg2.connect(
            host=db_config.host,
            port=db_config.port,
            user=db_config.username,
            password=db_config.password,
            # TODO: This may not be optimal. See https://stackoverflow.com/questions/2370525/default-database-named-postgres-on-postgresql-server
            dbname='postgres',
            connect_timeout=5)
        conn.autocommit = True
        with conn.cursor() as cur:
            cur.execute("SELECT count(*) FROM pg_stat_activity WHERE datname = '%s';" % dbname)
            live_connections = cur.fetchone()[0]
            logger.debug("DB %s has %d live connections" % (dbname, live_connections))
            while live_connections > 0:
                # Terminate any existing connections to the database
                cur.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%s';" % dbname)
                time.sleep(1.0)
                cur.execute("SELECT count(*) FROM pg_stat_activity WHERE datname = '%s';" % dbname)
                live_connections = cur.fetchone()[0]
                logger.debug("DB %s has %d live connections" % (dbname, live_connections))

            cur.execute("DROP DATABASE IF EXISTS '%s';" % dbname)
            cur.execute("CREATE DATABASE %s;" % dbname)
            logger.info("completed initialization of postgresql DB %s" % dbname)
    except (psycopg2.DatabaseError, psycopg2.OperationalError) as e:
        # If we fail, continue anyway
        logger.error(f'Error {e}')
    finally:
        if conn:
            conn.close()


def init_oracle_client():
    lib_dir = os.getenv('LD_LIBRARY_PATH')
    if lib_dir:
        logger.debug("Initializing oracle client in %s" % lib_dir)
    else:
        raise ValueError("Error initializing Oracle. 'LD_LIBRARY_PATH' is not defined. \n" +
                         "Should be where the oracle client libs are installed")
    cx_Oracle.init_oracle_client(lib_dir=lib_dir)


def init_oracle_db(db_config, username):
    logger.info("initializing Oracle user %s in service %s" % (username, db_config.service_name))
    conn = None
    # Connect as the system user to drop/create the server accounts
    try:
        conn_info = {
            'host': db_config.host,
            'port': db_config.port,
            'user': db_config.system_username,
            'psw': db_config.system_password,
            'service': db_config.service_name
        }
        conn_str = '{user}/{psw}@{host}:{port}/{service}'.format(**conn_info)
        conn = cx_Oracle.connect(conn_str)
        # TODO: How to set a connect_timeout?
        with conn.cursor() as cur:
            cur.execute('ALTER SESSION SET "_ORACLE_SCRIPT"=TRUE')
            cur.execute("SELECT sid,serial# FROM v$session where username='%s'" % (username.upper()))
            for row in cur.fetchall():
                logger.debug("killing session " + str(row))
                cur.execute("ALTER SYSTEM kill session '%s,%s' immediate" % row)
            # TODO: check if user exists

            cur.execute("SELECT COUNT(*) FROM dba_users WHERE username='%s'" % username.upper())
            count = cur.fetchone()[0]
            if count:
                logger.debug("Dropping user %s" % username)
                cur.execute("DROP USER %s CASCADE" % username)
            logger.debug("Creating user %s" % username)
            cur.execute("CREATE USER %s IDENTIFIED BY %s" % (username, db_config.password))
            cur.execute("GRANT ALL PRIVILEGES TO %s" % username)
            conn.commit()
            logger.info(
                "completed initialization of Oracle user %s in service %s" % (username, db_config.service_name))
    finally:
        if conn:
            conn.close()


def init_db(db_config, initialize_db=False):
    """
    Generate Spring Datasource properties for an external DB configuration (currently postgresql or oracle).
    Args:
        db_config : the DBConfig
        initialize_db : if True, Initialize the Skipper and Dataflow database: kill current connections, create user, dbname.
                    The credentials provided must have the required privileges. These are the same credentials used for
                    the apps that access the DB (Dataflow, Composed Task Runner, and any Task apps).

                    If False, the SQL database must exist. The credentials must have privileges to create tables, etc.

    Returns:
        Spring datasource properties for the Skipper and Dataflow instances. They can be the same or different.

    """

    if not (db_config and db_config.provider):
        logging.info("'database provider' is not defined. Skipping external DB initialization.")
        return

    if db_config.provider.is_postrgesql():
        if initialize_db:
            init_postgres_db(db_config, db_config.dataflow_db_name)
            if db_config.skipper_db_name != db_config.dataflow_db_name:
                init_postgres_db(db_config, db_config.skipper_db_name)

        driver_class_name = 'org.postgresql.Driver'
        logger.debug("Building postgres url with dataflow_db_name=%s and skipper_db_name=%s" % (
            db_config.dataflow_db_name, db_config.skipper_db_name))
        skipper_url = "jdbc:postgresql://%s:%d/%s?user=%s&password=%s" % (
            db_config.host, int(db_config.port), db_config.skipper_db_name, db_config.username, db_config.password)
        dataflow_url = "jdbc:postgresql://%s:%d/%s?user=%s&password=%s" % \
                       (db_config.host, int(db_config.port), db_config.dataflow_db_name, db_config.username,
                        db_config.password)
        return {
            "dataflow": DatasourceConfig(url=dataflow_url,
                                         name=db_config.dataflow_db_name,
                                         username=db_config.username,
                                         password=db_config.password,
                                         driver_class_name=driver_class_name),
            "skipper": DatasourceConfig(url=skipper_url,
                                        name=db_config.skipper_db_name,
                                        username=db_config.username,
                                        password=db_config.password,
                                        driver_class_name=driver_class_name)
        }

    elif db_config.provider.is_oracle():
        if initialize_db:
            init_oracle_client()
            '''Oracle creates different user for each. Using the same DB service'''
            init_oracle_db(db_config, db_config.dataflow_db_name)
            if db_config.skipper_db_name != db_config.dataflow_db_name:
                init_oracle_db(db_config, db_config.skipper_db_name)

        skipper_url = dataflow_url = "jdbc:oracle:thin:@%s:%d:%s" % (
            db_config.host, int(db_config.port), db_config.service_name)
        driver_class_name = 'oracle.jdbc.OracleDriver'
        return {
            "dataflow": DatasourceConfig(url=dataflow_url,
                                         name=db_config.dataflow_db_name,
                                         username=db_config.dataflow_db_name,
                                         password=db_config.password,
                                         driver_class_name=driver_class_name),
            "skipper": DatasourceConfig(url=skipper_url,
                                        name=db_config.skipper_db_name,
                                        username=db_config.skipper_db_name,
                                        password=db_config.password,
                                        driver_class_name=driver_class_name)
        }

    # Impossible. DbConfig does the validation
    # else:
    #     raise ValueError("Sorry, SQL provider %s is invalid or unsupported." % db.provider)
