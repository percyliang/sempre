
## Basics

1) Start the server
    ./interactive/run @mode=voxlurn -Server.port 8410
    ./interactive/run @mode=voxlurn -Server.port 8410
2) Blast the server with simulator on previous logs, under sandbox mode, to get the server into state
3) Collect and append to more logs
4) For any particular experiment, save the previous log
    ./interactive/run @mode=backup # save previous data logs
    ./interactive/run @mode=trash # deletes previous data logs

## Tests
    ./shrdlurn/run @mode=test
    ./shrdlurn/run @mode=test @class=ActionExecutorTest -verbose 5
