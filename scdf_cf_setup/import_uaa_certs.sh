#!/usr/bin/env bash
if [[ -z "$1" ]]; then
  echo "No cert_host provided"
  echo "Usage $0 cert_host"
  exit 1
fi

CERT_HOST=$1
IMPORT_FILE="${CERT_HOST}.cer"
OPEN_SSL_CMD="openssl s_client -connect ${CERT_HOST}:443 -showcerts"
echo "Running $OPEN_SSL_CMD"
$OPEN_SSL_CMD > $IMPORT_FILE < /dev/null

if [[ ! -s "$IMPORT_FILE" ]]; then
 echo "Failed to import certs from ${CERT_HOST}"
 rm -f $IMPORT_FILE
 exit 1
fi
echo "imported certs to ${CERT_HOST}.cer"
exit 0



