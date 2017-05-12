#!/bin/bash

set -o errexit

kubectl create -f kafka.yml
