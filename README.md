# TIS Common Upload

## About
TIS common upload to be used for upload files in S3.

## TODO
 - Provide `SENTRY_DSN` and `SENTRY_ENVIRONMENT` as environmental variables
   during deployment.
 - Set up Sentry project.
 - Provide `SENTRY_DSN` and `SENTRY_ENVIRONMENT` as environmental variables
    during deployment.
 - Add repository to SonarCloud.
 - Add SonarCloud API key to repository secrets.
 - Add repository to Dependabot.  
   
## Workflow
The `CI/CD Workflow` is triggered on push to any branch.

![CI/CD workflow](.github/workflows/ci-cd-workflow.svg "CI/CD Workflow")

## Versioning
This project uses [Semantic Versioning](semver.org).

## License
This project is license under [The MIT License (MIT)](LICENSE).

[task-definition]: .aws/task-definition.json
