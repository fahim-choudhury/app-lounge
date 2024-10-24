## How to install Maestro
Please follow the instructions at: https://maestro.mobile.dev/getting-started/installing-maestro

## How to test

Run this command from the **maestro** directory of the project.

Debug:
```shell
maestro test -e APP_ID=foundation.e.apps.debug maestro-apps-install.yaml
```

Release:
```shell
maestro test -e APP_ID=foundation.e.apps maestro-apps-install.yaml
```