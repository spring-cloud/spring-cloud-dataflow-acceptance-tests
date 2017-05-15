#!/bin/bash

set -o errexit

kubectl create -f mysql.yml
