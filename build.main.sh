#!/bin/sh

CP=`cat $1`
CP_=$(echo $CP | tr ":" "\n")

for path in $CP_ ; do
  if [[ "$path" == *"org.strategoxt.imp.nativebundle"* ]]; then
    NATIVE_JAR=$path
  fi
done

echo $NATIVE_JAR
mkdir native
(cd native && jar xf $NATIVE_JAR)

LOCALCLASSPATH=$CP ant -f build.main.xml -Dcompile.classpath=$CP -Declipse.spoofaximp.nativeprefix=native/native/linux shell-all

rm -rf native
rm $1


