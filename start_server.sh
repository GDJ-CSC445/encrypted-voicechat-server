#!/bin/bash

# Shell script for cs server easy deployment
mvn clean
mvn compile
mvn assembly:single
clear
java -cp target/encrypted-voicechat-server-1.0-SNAPSHOT-jar-with-dependencies.jar edu.oswego.cs.App