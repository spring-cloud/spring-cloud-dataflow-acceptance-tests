. $ROOT_DIR/scripts/utility-functions.sh
run_scripts "$ROOT_DIR/$PLATFORM/apps" "register-apps.sh" "Authorization:$(cf oauth-token)"
