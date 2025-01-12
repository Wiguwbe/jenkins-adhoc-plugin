# adhoc

## Introduction

This plugin adds an CLI command to run _ad-hoc_ (or _one-of_) jobs.

## Getting started

_(No configuration needed)_

The command expects a `tar` file (uncompressed) in the `stdin` -- that is the
workspace for the job. It should contain a `Jenkinsfile` (or with a different
name/location according to the `-jenkinsfile` option).

It runs the pipeline, streaming the logs to `stderr` and, at the end, stream
another `tar` file (also uncompressed) to `stdout` comprising of the build
artifacts.

Example usage (also see `run-pipeline.sh` and `testws/`):
```
tar -c --exclude-vsc-ignores --exclude-vcs . \
| java -jar jenkins-cli.jar adhoc \
| tar -C ../build -t
```

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

