Compositional Semantic Parsing on Semi-Structured Tables
========================================================

This README file describes the `tables` mode which accompanies the paper:

> Panupong Pasupat, Percy Liang.  
> Compositional Semantic Parsing on Semi-Structured Tables  
> Association for Computational Linguistics (ACL), 2015.

Further descriptions and experiments can be viewed on the CodaLab website:

https://www.codalab.org/worksheets/0xf26cd79d4d734287868923ad1067cf4c/

Running the code
----------------

1. Download the dependencies and the dataset:

        ./pull-dependencies core
        ./pull-dependencies corenlp
        ./pull-dependencies tables
        ./pull-dependencies tables-data

  The dataset lives in `lib/data/tables/`

2. Compile the source:

        ant tables

  This will produce JAR files in the `libsempre` directory as usual.

3. The following command train and test on 100 development examples:

        ./run @mode=tables @data=u-1 @feat=all @train=1 -maxex train,100 dev,100

  The command should take less than an hour.

  * To train on the complete development set, remove `-maxex train,100 dev,100`

  * The command above uses `u-1` (80:20 split of the development data).
  Other available sets include `u-2`, ..., `u-5` (four other development splits)
  and `test` (actual train-test split).

Other usages
------------

To launch the interactive shell, use:

    ./run @mode=tables -interactive

Apart from the usual shell commands, the additional command `context`
can load the context graph for execution. For example, use:

    (context (graph tables.TableKnowledgeGraph csv/204-csv/590.csv))

to load `lib/data/tables/csv/204-csv/590.csv`

The table can also be viewed in pretty-printed format by calling

    ./tables/view 204 590

or

    ./tables/view csv/204-csv/590.csv
