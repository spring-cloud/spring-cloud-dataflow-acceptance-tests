#!/usr/bin/env bash
pushd $SETUP_TOOL_REPO  > /dev/null
    echo "PYTHONPATH=$PYTHONPATH"
    echo "Cleanup CF environment"
    python3 -m install.clean -v
popd > /dev/null
