#!/bin/sh

CP=`cat $1`
CP_=$(echo $CP | tr ":" "\n")

for path in $CP_ ; do
  MATCH=`echo $path | sed 's/.*org\.strategoxt\.imp\.nativebundle.*/ok/'`
  if [ "$MATCH" = "ok" ]; then
    NATIVE_JAR=$path
  fi
done

mkdir native
(cd native && jar xf $NATIVE_JAR)

LOCALCLASSPATH=$CP ant -f build.main.xml -Dcompile.classpath=$CP -Declipse.spoofaximp.nativeprefix=native/native/linux shell-all \
&& rm -rf native \
&& rm $1


