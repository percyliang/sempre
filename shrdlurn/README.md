Run: ./run @mode=interactive -Server.port 8410
Test command : ./run @mode=test @class=interactive.actions.ActionExecutorTest -verbose 5
Extracting query from sempre log:
awk -F"\t" '{sub("query=","",$5); print $5}'
cat ~/git/sidaw_acl2015/int-output/logs/_*.log | awk -F"\t" '{sub("query=","",$5); if (index($5, "(:def")!=0 ) print $5;}'
