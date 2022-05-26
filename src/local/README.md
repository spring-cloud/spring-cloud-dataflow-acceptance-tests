# Configure MiniKube for Local testing of Spring Cloud Dataflow

You need docker credentials for the various docker containers.

After `docker login` use `cat ~/.docker/config.json` to find the docker secret. set DOCKER_SECRET in your env.

Configure and start minikube and deploy the helm chart for Spring Cloud Dataflow.

```shell
./src/local/configure-minikube.sh
```

```shell
./src/local/helm/deploy-scdf.sh
```

Wait for the containers to enter `Running` state.
Use `kubectl get pods --all-namespaces` to view the pods.

```shell
./src/local/helm/forward-scdf.sh
./src/local/register-apps.sh
```
