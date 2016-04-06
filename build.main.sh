#!/bin/sh

BASEDIR=`pwd $(dirname $0)`

CP=`cat $1`

CP_=$(echo $CP | tr ":" "\n")
NEW_CP=""
EMPTY=true

for path in $CP_ ; do
  MATCH=`echo $path | sed 's/.*org\.strategoxt\.imp\.nativebundle.*/ok/'`
  if [ "$MATCH" = "ok" ]; then
    NATIVE_JAR=$path
  else 
    if $EMPTY; then
      NEW_CP=$path
	  EMPTY=false
    else
      NEW_CP="$NEW_CP:$path"
    fi
  fi
  MATCH=`echo $path | sed 's/.*org\.strategoxt\.strj.*/ok/'`
  if [ "$MATCH" = "ok" ]; then
    STRATEGO_JAR=$path/java/strategoxt.jar
  fi
done

echo "Native JAR"
echo $NATIVE_JAR
echo "Stratego JAR"
echo $STRATEGO_JAR

echo 
echo $CP
echo

mkdir -p native
(cd native && jar xf $NATIVE_JAR)

NEW_CP="$NEW_CP:$BASEDIR/native/"

OS='linux'
UNAME=`uname`
if [ "$UNAME" = "Darwin" ]; then
  OS="macosx"
fi

echo "SDF to table"
echo "native/native/$OS/sdf2table"

chmod +x native/native/$OS/sdf2table
chmod +x native/native/$OS/implodePT
LOCALCLASSPATH=$CP ant -f build.main.xml \
  -Dcompile.classpath=$NEW_CP \
  -Declipse.spoofaximp.nativeprefix=$BASEDIR/native/native/$OS/ \
  -Declipse.spoofaximp.strategojar=$STRATEGO_JAR \
  shell-all ## \

