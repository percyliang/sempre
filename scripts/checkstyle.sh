# Check style of the code

if [ -z "$1" ]; then
  files=`find src -name "*.java"`
else
  files="$@"
fi

d=`dirname $0`
java -cp $d/../lib/checkstyle/checkstyle-6.1.1-all.jar com.puppycrawl.tools.checkstyle.Main -c `dirname $0`/checkstyle.xml $files
