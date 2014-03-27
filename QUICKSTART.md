This is a quickstart guide for recreating the EMNLP 2013 system.

# Download the Dependencies

These commands will download necessary resources:

    ./download-dependencies core
    ./download-dependencies emnlp2013
    ./download-dependencies fullfreebase_vdb

See `README.md` for other requirements SEMPRE depends on
(JDK 7 and Ruby).

# Install the Database

Freebase is stored in a database called virtuoso. These commands will
install a copy of it. Make sure to execute the `git checkout tags/v7.0.0`
to ensure you have a compatible version of virtuoso (instead of the most
recent version).

    # For Ubuntu, make sure these dependencies are installed
    sudo apt-get install -y automake gawk gperf libtool bison flex libssl-dev
    # Clone into sempre folder
    git clone https://github.com/openlink/virtuoso-opensource
    cd virtuoso-opensource
    git checkout tags/v7.0.0
    ./autogen.sh
    ./configure --prefix=$PWD/install
    make
    make install
    cd ..

# Start the Database

This will start the virtuoso database on `localhost:3093` and import freebase:

    ./scripts/virtuoso start lib/freebase/93.exec/vdb 3093

# Running the System on New Questions

First make sure you have compiled sempre by running `make`.

Create a file called `testinput` that has your test questions in this format:

    (example (utterance "what states make up the midwest us?") (targetValues (description "")))
    (example (utterance "what is the capital of france?") (targetValues (description "Paris")))

Then run this command to test the default trained system on those two examples:

    ./sempre @mode=train \
             @domain=webquestions \
             @sparqlserver=localhost:3093 \
             @cacheserver=local \
             -Learner.maxTrainIters 0 \
             -Dataset.inPaths test:testinput \
             -Builder.inParamsPath lib/models/15.exec/params \
             -Grammar.inPaths lib/models/15.exec/grammar \
             -Dataset.readLispTreeFormat true

This run should take about a minute or two.  This will save the output to
`state/execs/$N.exec/log` where `$N` is some number.  The current system should
get the first example wrong and the second one correct.

Alternatively, you can launch an interactive shell to test out the system:

    ./sempre @mode=interact \
             @domain=webquestions \
             @sparqlserver=localhost:3093 \
             @cacheserver=local \
             @load=15\
             @executeTopOnly=0

# Training the System

This command will train the system on the WebQuestions dataset. It takes
a little over three days to complete.

    ./sempre @mode=train \
             @sparqlserver=localhost:3093 \
             @domain=webquestions \
             @cacheserver=local

Alternatively, you can sanity check the system on the Free917 dataset by
setting `@domain=free917`. This should take a few hours to complete.
