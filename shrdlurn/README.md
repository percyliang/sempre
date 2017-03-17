Experimental work following
1) ./shrdlurn/runServer.sh to start both servers
2) Blast the server with simulator on previous logs, under sandbox mode, to get the server into state
3) Collect and append to more logs
4) For any particular experiment, save the previous log
	- ./run @mode=int-utils @cmd=backup-mv
  - reset the log
potentially reset citation and grammar information as well?
5) blast server with non-sandbox call to get previous citations

Default port: 8410 for sempre, 8406 for user
Run: ./run @mode=interactive -Server.port 8410
Test command : .shrdlurn/run @mode=test @class=interactive.test.ActionExecutorTest -verbose 5
Test command : .shrdlurn/run @mode=test -verbose 5
