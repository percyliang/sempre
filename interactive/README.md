# README

This `interactive` package is the code for our paper
*Naturalizing a programming language through interaction* (ACL 2017)

Voxelurn is a language interface to a voxel world.
This server handles commands used to learn from definitions, and other interactive queries.
In this setting, the system begin with the dependency-based action language (`dal.grammar`), and gradually expand the language through interacting with it users.


## Running the Voxelurn server

0. Setup SEMPRE dependencies and compile

         ./pull-dependencies core
         ant interactive

1. Start the server

        ./interactive/run @mode=voxelurn -server -interactive

  things in the core language such as `add red left`, `repeat 3 [select left]` should work.

2. Feed the server existing definitions, which should take less than 2 minutes.

        ./interactive/run @mode=simulator @server=local @sandbox=none @task=freebuilddef -maxQueries 2496

  try `add dancer`  now.

### Interacting with the server

There are 3 ways to interact and try your own commands

* Hit `Ctrl-D` on the terminal running the server, and type `add red top`, or `add green monster`

* On a browser, type `http://localhost:8410/sempre?q=(:q add green monster)`

* The visual way is to use our client at `https://github.com/sidaw/shrdlurn`, which has a more detailed [README.md](https://github.com/sidaw/shrdlurn/blob/master/README.md). Try `[add dancer; front 5] 3 times` after you run the client. A live version is at [voxelurn.com](http://www.voxelurn.com).

## Experiments in ACL2017

1. Start the server

      ./interactive/run @mode=voxelurn -server -interactive

2. Feed the server all the query logs

      ./interactive/run @mode=simulator @server=local @sandbox=none @task=freebuild -maxQueries 103876

  This currently takes just under 30 minutes. Decrease maxQuery for a quicker experiment. This generate `plotInfo.json` in `../state/execs/${lastExec}.exec/` where `lastExec` is `cat ../state/lastExec`.

3. Taking `../state/execs/${lastExec}.exec/plotInfo.json` as input, we can analyze the data and produce some plots using the following ipython notebook

       ipython notebook interactive/analyze_data.ipynb 

  which prints out basic statistics and generates the plots used in our paper. The plots are saved at `../state/execs/${lastExec}.exec/`


## Misc.

There are some unit tests

    ./interactive/run @mode=test

To specify a specific test class and verbosity

    ./interactive/run @mode=test @class=DALExecutorTest -verbose 5

Clean up or backup data

    ./interactive/run @mode=backup # save previous data logs
    ./interactive/run @mode=trash # deletes previous data logs

Data, in .gz can be found in queries.

* `./interactive/queries/freebuildbig-0206.def.json.gz`
has 2495 definitions combining just over 10k utterances.
* `./interactive/queries/freebuildbig-0206.json.gz` has 103875 queries made during the main experiment.

## Client server (optional and in development)

This server helps with client side logging, leaderboard, authentication etc. basically anything that is not directly parsing.

    cd interactive
    python community-server/install-deps.py
    export SEMPRE_JWT_SECRET=sdlfdsaklafsl
    export SLACK_SECRET=somekeyyougetfromslack
    python community-server/server.py --port 8403
