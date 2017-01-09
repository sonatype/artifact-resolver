#!/bin/sh
set -e
cp LICENSE src/artifact-resolver-LICENSE.txt
cp scriptjar.groovy src
cd src
groovy scriptjar.groovy artifactResolver.groovy artifactResolver.jar "simplelogger.properties" "artifact-resolver-LICENSE.txt"
mv artifactResolver.jar ../
rm artifact-resolver-LICENSE.txt
rm scriptjar.groovy
cd -
