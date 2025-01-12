#!/bin/sh

set -eu

# TODO get from parameters
workspace=testws
outdir=out

test -f jenkins-cli.jar || {
    wget http://localhost:8080/jenkins/jnlpJars/jenkins-cli.jar
}

pwd_real=$(realpath $(pwd))
out_real=$(realpath ${outdir})

# clean (unless it's the current directory)
test "$pwd_real" != "$out_real" && rm -rf $outdir
mkdir -p $outdir

tar -C $workspace -c . | \
    java -jar jenkins-cli.jar \
    -s http://localhost:8080/jenkins/ \
    adhoc \
        -jenkinsfile test.jenkins \
        -uid $(id -u) -gid $(id -g) | \
        tar -C $outdir -x