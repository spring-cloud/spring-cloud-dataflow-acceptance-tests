schedulesEnabled=$1
run_scripts "mysql" "destroy.sh"
if [ "$schedulesEnabled" ]; then
        echo "Destroy scheduler"
        run_scripts "scheduler" "destroy.sh"
fi
pushd "binder"
      run_scripts $BINDER "destroy.sh"
popd
echo "Clean up servers"
run_scripts "server" "destroy.sh"
run_scripts "skipper-server" "destroy.sh"

    