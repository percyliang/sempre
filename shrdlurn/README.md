# README

## Running the server for voxelurn

1) Start the sempre server

    ./interactive/run @mode=voxelurn -Server.port 8410

1) Start the client server

    ./interactive/run @mode=community

2) (optionallly) Blast the server with previous logs to get the server into state

    ./interactive/run @mode=simulator @server=local @sandbox=none @task=freebig

## Running an experiment

1) Start the server

    ./interactive/run @mode=voxelurn -Server.port 8410

2) last the server with previous logs to get the server into state

    ./interactive/run @mode=simulator @server=local @sandbox=none @task=freebig

2) Run analysis script to get results

    ./interactive/run @mode=analyze -execNumber 1
0) (Optional) clean up

    ./interactive/run @mode=backup # save previous data logs
    ./interactive/run @mode=trash # deletes previous data logs

## Tests

There are many units for interactive learning

    ./interactive/run @mode=test
To specify a specific test class and verbosity

    ./interactive/run @mode=test @class=DACExecutorTest -verbose 5
