# Check style of the code

if [ -z "$1" ]; then
  files=`find src -name "*.java"`
else
  files="$@"
fi

d=`dirname $0`
prog="$d/../lib/checkstyle/checkstyle-6.6-all.jar"
java -cp $prog com.puppycrawl.tools.checkstyle.Main -c `dirname $0`/checkstyle.xml $files
