set +e
#set -o pipefail
if [ -f "${ROOT_DIR}/$CERT_URI.cer" ]; then
    rm -f "${ROOT_DIR}/$CERT_URI.cer"
fi
# Delete any remaining apps before deleting services since some may be bound to them.
for app in $(cf apps | tail +5 | awk '{print $1}');
do
        cf delete -f -r $app
done

cf delete-orphaned-routes -f

