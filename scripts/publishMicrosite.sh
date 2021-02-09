#!/bin/bash
set -e

git config --global user.email "regis.kuckaertz@gmail.com"
git config --global user.name "regiskuckaertz"
git config --global push.default simple

sbt startDynamodbLocal docs/publishMicrosite stopDynamodbLocal