#!/bin/bash
set -ev

VERSION=$(./mvnw -q -Dexec.executable="echo" -Dexec.args='${project.version}' --non-recursive org.codehaus.mojo:exec-maven-plugin:1.5.0:exec)
echo "Building PMD Build Tools ${VERSION} on branch ${TRAVIS_BRANCH}"

if [[ "$VERSION" != *-SNAPSHOT && "$TRAVIS_TAG" != "" ]]; then
    # release build
    ./mvnw deploy -Possrh,pmd-release -B -V
elif [[ "$VERSION" == *-SNAPSHOT ]]; then
    # snapshot build
    ./mvnw deploy -Possrh -B -V
else
    # other build. Can happen during release: the commit with a non snapshot version is built, but not from the tag.
    ./mvnw verify -Possrh -B -V
    # we stop here - no need to execute further steps
    exit 0
fi
