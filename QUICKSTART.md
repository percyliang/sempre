This is a quickstart guide for recreating the EMNLP 2013 system.

# Download the Dependencies

These commands will download necessary resources:

    ./download-dependencies core 
    ./download-dependencies emnlp2013 
    ./download-dependencies fullfreebase 
    ./download-dependencies fullfreebase_ttl 
    ./download-dependencies fullfreebase_vdb

# Install the Database

Freebase is stored in a database called virtuoso. These commands will 
install a copy of it. Make sure to execute the `git checkout tags/v7.0.0`
to ensure you have a compatible version of virtuoso (instead of the most 
recent version).

    git clone https://github.com/openlink/virtuoso-opensource
    cd virtuoso-opensource
    git checkout tags/v7.0.0
    ./autogen.sh
    ./configure --prefix=$PWD/install
    make
    make install
    cd ..

# Start the Database

This will start the virtuoso database on `localhost:3001` and import freebase:

    ./scripts/virtuoso start lib/freebase/93.exec/vdb 3001

# Running the System on New Questions

Create a file called `testinput` that has your test questions in this format:

    (example (utterance "what states make up the midwest us?") (targetValues (description "")))
    (example (utterance "what is the capital of france?") (targetValues (description "")))

Then run this command:

    ./sempre @mode=train \
             @domain=webquestions \
             @sparqlserver=localhost:3001 \
             @cacheserver=local \
             -Dataset.inPaths test,testinput \
             -Builder.inParamsPath lib/models/2174.exec/params \
             -Grammar.inPaths lib/models/2174.exec/grammar \
             -Dataset.readLispTreeFormat true

This will save the output to `state/execs/$N.exec/log` where `$N` is some 
number. 


# Training the System

This command will train the system on the WebQuestions dataset. It takes
a little over three days to complete.

    ./sempre @mode=train \
             @sparqlserver=localhost:3001 \
             @domain=webquestions \
             @cacheserver=local
