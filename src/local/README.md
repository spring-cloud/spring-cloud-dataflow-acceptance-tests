# Configure MiniKube for Local testing of Spring Cloud Dataflow

In order to ensure to manage the specific versions of data flow and skipper containers you can set SKIPPER_VERSION and DATAFLOW_VERSION environmental variable and then invoke `./src/local/pull-dataflow.sh` and `./src/local/pull-skipper.sh`

If you want to use a locally built image you can invoke `./src/loca/build-skipper-image.sh` or `./src/local/build-dataflow.sh`

Configure and start minikube and deploy the helm chart for Spring Cloud Dataflow.

You can change the type of driver used by setting `MK_DRIVER` to one of `kvm2, docker, vmware, virtualbox, podman, vmwarefusion`

```shell
./src/local/configure-k8s.sh
```

Launch the k8s containers.
Adding `helm` as first parameter will use the bitnami helm chart which is limited to released version and their containers.

```shell
./src/local/launch-scdf.sh
```

Stop the portforward and delete minikube.

```shell
./src/local/destroy-k8s.sh
```
