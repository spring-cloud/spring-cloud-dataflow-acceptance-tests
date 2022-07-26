#!/usr/bin/env bash
pushd $SETUP_TOOL_REPO  > /dev/null
    echo "PYTHONPATH=$$PYTHONPATH"
    echo "PWD=$PWD"
    # If tests fail, clean up anyway.
    python3 -m install.clean -v
popd > /dev/null
