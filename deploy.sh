#!/bin/bash

cd /var/mahoutX/
mkdir -p /usr/share/maven-repo/log4j/log4j/1.2.15
mkdir -p /usr/share/maven-repo/javax/jms/jms/1.1
mkdir -p /usr/share/maven-repo/com/sun/jdmk/jmxtools/1.2.1
mkdir -p /usr/share/maven-repo/com/sun/jmx/jmxri/1.2.1/
mkdir -p /usr/share/maven-repo/org/codehaus/woodstox/wstx-asl/3.2.9
cp jars/log4j-1.2.15.jar /usr/share/maven-repo/log4j/log4j/1.2.15/.
cp jars/jms-1.1.jar /usr/share/maven-repo/javax/jms/jms/1.1/.
cp jars/jmxtools-1.2.1.jar /usr/share/maven-repo/com/sun/jdmk/jmxtools/1.2.1/.
cp jars/jmxri-1.2.1.jar /usr/share/maven-repo/com/sun/jmx/jmxri/1.2.1
cp jars/wstx-asl-3.2.9.jar /usr/share/maven-repo/org/codehaus/woodstox/wstx-asl/3.2.9

mvn package





