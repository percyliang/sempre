# README


## Running an experiment

1) Start the server

    ./interactive/run @mode=voxelurn -server

2) Query the server with existing data

    ./interactive/run @mode=simulator @server=local @sandbox=none @task=freebig

3) Run analysis script to get results

    ./interactive/run @mode=analyze

4) (Optional) clean up

    ./interactive/run @mode=backup # save previous data logs
    ./interactive/run @mode=trash # deletes previous data logs

## Running the server for voxelurn

1) Start the sempre server

    ./interactive/run @mode=voxelurn -server

2) Start the client server

    ./interactive/run @mode=community

2) (Optionally) Query the server with existing data and use previous definitions

    ./interactive/run @mode=simulator @server=local @sandbox=none @task=freebigdef


## Tests

There are many units for interactive learning

    ./interactive/run @mode=test

To specify a specific test class and verbosity

    ./interactive/run @mode=test @class=DALExecutorTest -verbose 5

Test in interactive mode

    ./interactive/run @mode=voxelurn -interactive
