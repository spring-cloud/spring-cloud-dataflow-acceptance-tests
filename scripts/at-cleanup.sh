#!/bin/bash
pushd $SETUP_TOOL_REPO  > /dev/null || exit 1
    echo "PYTHONPATH=$PYTHONPATH"
    echo "Cleanup CF environment"
    python3 -m install.clean -v
popd > /dev/null || exit 1
