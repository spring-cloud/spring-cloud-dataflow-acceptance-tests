#!/usr/bin/env bash

MK_DRIVER=docker
# MK_DRIVER=kvm2, docker, vmware, virtualbox, podman, vmwarefusion
minikube start --cpus=4 --memory=10240 --driver=$MK_DRIVER
