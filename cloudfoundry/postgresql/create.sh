python3 $ROOT_DIR/scripts/python/config_external_db.py
export SPRING_DATASOURCE_URL: "jdbc:oracle:thin:@${SQLHOST}:${SQL_PORT}:xe"
export SPRING_DATASOURCE_USERNAME=$SQL_USERNAME
export SPRING_DATASOURCE_PASSWORD=$SQL_PASSWORD
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver
jdbc:postgresql://34.66.47.209:5432/skipper_pro_1_5_0_kafka_0