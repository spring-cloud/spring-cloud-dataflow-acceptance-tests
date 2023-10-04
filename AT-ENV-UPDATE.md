# Updating CF AT Environments

## Update descriptors
See https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/commit/ffc66a4b3ab11149208e04405bf8870ea3039633

## Update secrets
Navigate to https://github.com/spring-cloud/spring-cloud-dataflow-acceptance-tests/settings/secrets/actions and update these secrets:
* CF_ENV_0_PASSWORD
* CF_ENV_1_PASSWORD

## Manually Create CF Spaces
Manually create the CF spaces else you will see this:
```
cloudfoundry.cli - 2023-10-04 15:32:38,472 - DEBUG:  connection config:
{
    "env": {
        "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD": "********",
        "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE": "kafka",
        "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION": "false",
        "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN": "apps.bucharest.cf-app.com",
        "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG": "p-dataflow",
        "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME": "********",
        "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_TASK_SERVICES": "scdf-scheduler",
        "SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL": "https://api.sys.bucharest.cf-app.com"
    },
    "api_endpoint": "https://api.sys.bucharest.cf-app.com",
    "org": "p-dataflow",
    "space": "kafka",
    "app_domain": "apps.bucharest.cf-app.com",
    "username": "********",
    "password": "********",
    "skip_ssl_validation": false,
    "scheduler_url": null
}
cloudfoundry.cli - 2023-10-04 15:32:38,570 - DEBUG:  FAILED

cloudfoundry.cli - 2023-10-04 15:32:38,570 - DEBUG:  current target:
{}
cloudfoundry.cli - 2023-10-04 15:32:38,571 - DEBUG:  logging in to CF - api: https://api.sys.bucharest.cf-app.com org: p-dataflow space: kafka
cloudfoundry.cli - 2023-10-04 15:32:42,295 - DEBUG:  api endpoint:   https://api.sys.bucharest.cf-app.com
api version:    2.209.0
user:           ***
org:            p-dataflow
No space targeted, use 'cf target -s SPACE'
```
