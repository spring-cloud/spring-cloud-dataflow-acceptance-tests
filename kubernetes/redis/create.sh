#!/bin/bash

set -o errexit

kubectl create -f redis.yml
