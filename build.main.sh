#!/bin/sh

CP=`cat $1`
LOCALCLASSPATH=$CP ant -f build.main.xml -Dcompile.classpath=$CP shell-all
rm $1

