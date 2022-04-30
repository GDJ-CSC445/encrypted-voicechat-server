#!/bin/bash

# Shell script for cs server easy deployment
mvn clean
mvn package
mvn assembly:single
clear
java -jar target/encrypted-voicechat-server-1.0-SNAPSHOT-jar-with-dependencies.jar # edu.oswego.cs.VoicechatServer