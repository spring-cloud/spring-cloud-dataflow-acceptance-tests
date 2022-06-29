#!/usr/bin/env bash
FWSCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
SC_PATH=$(realpath $FWSCDIR)
set -e
echo "Waiting for dataflow"
kubectl rollout status deployment scdf-server
source $SC_PATH/export-dataflow-ip.sh
while true
do
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' $DATAFLOW_IP)
  if [ $STATUS -eq 200 ]; then
    echo "$DATAFLOW_IP active"
    break
  else
    echo "Waiting for $DATAFLOW_IP $STATUS"
  fi
  sleep 3
done
kubectl port-forward --namespace default svc/scdf-server "9393:9393" &
if [ "$PROMETHEUS" == "true" ]; then
  kubectl port-forward --namespace default svc/grafana "3000:3000" &
fi

export DATAFLOW_IP="http://localhost:9393"
echo "DATAFLOW_IP=$DATAFLOW_IP"
