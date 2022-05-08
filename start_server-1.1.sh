#!/bin/bash

# Shell script for cs server easy deployment
# Problem on the Oswego servers where the version of java does not work with maven
export JAVA_HOME=/usr/lib/jvm/java-1.11.0-openjdk-amd64
source /etc/environment

mvn clean
mvn package
mvn assembly:single
clear
java -jar target/encrypted-voicechat-server-1.0-SNAPSHOT-jar-with-dependencies.jar # edu.oswego.cs.VoicechatServer