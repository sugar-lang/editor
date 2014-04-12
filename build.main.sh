#!/bin/sh

BASEDIR=`pwd $(dirname $0)`

CP=`cat $1`
CP_=$(echo $CP | tr ":" "\n")

for path in $CP_ ; do
  MATCH=`echo $path | sed 's/.*org\.strategoxt\.imp\.nativebundle.*/ok/'`
  if [ "$MATCH" = "ok" ]; then
    NATIVE_JAR=$path
  fi
done
echo "Native Jar File"
echo $NATIVE_JAR

mkdir native
(cd native && jar xf $NATIVE_JAR)

OS='linux'
UNAME=`uname`
if [ "$UNAME" = "Darwin" ]; then
  OS="macosx"
fi

echo "SDF to table"
echo "native/native/$OS/sdf2table"

chmod +x native/native/$OS/sdf2table
chmod +x native/native/$OS/implodePT
LOCALCLASSPATH=$CP ant -f build.main.xml -Dcompile.classpath=$CP -Declipse.spoofaximp.nativeprefix=$BASEDIR/native/native/$OS/ shell-all ## \
#&& rm -rf native \
##&& rm $1

