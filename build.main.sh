#!/bin/sh

CP=`cat $1`
OPT_JAR_LIST=$CP ant -f build.main.xml -Dcompile.classpath=$CP shell-all
rm $1

