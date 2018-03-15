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

  The dataset lives in `lib/data/WikiTableQuestions/`

2. Compile the source:

        ant tables

  This will produce JAR files in the `libsempre` directory.

3. The following command train and test on 100 development examples:

        ./run @mode=tables @data=u-1 @feat=all @train=1 -maxExamples train:100 dev:100

  The command should take less than 30 minutes.

  * To train on the complete development set, remove `-maxExamples train:100 dev:100`

  * The command above uses `u-1` (80:20 split of the development data).
  Other available sets include `u-2`, ..., `u-5` (four other development splits)
  and `test` (actual train-test split).

Other options
-------------

### Macro Grammar (Experimental)

Macro grammar can be used to significantly speed up the parser.
To turn on macro grammar, run the following:

    ./run @mode=tables @data=u-1 @feat=more @parser=cprune @grammar=extended @fuzzy=editdist-fuzzy @train=1

Please refer to the following paper for more information:

> Yuchen Zhang, Panupong Pasupat, Percy Liang.  
> Macro Grammars and Holistic Triggering for Efficient Semantic Parsing  
> Empirical Methods on Natural Language Processing (EMNLP), 2017.

Currently the module does not support model saving, and testing has to be done on the official test set.
These features will be added in the future.

Official evaluation
-------------------

The official evaluation script in the WikiTableQuestions dataset is slightly
more lenient than the SEMPRE one (`tables.TableValueEvaluator`).
In particular, the SEMPRE evaluator enforces that the type of the predicted
denotation must match the correct answer type, while the official one allows
type conversion.

To get the official number of a trained model, run

    ./pull-dependencies tables-cprune
    ./run @mode=tables @data=u-1 @feat=all @train=0 -Derivation.showValues -Builder.inParamsPath path/to/params

(Change the `@data` other options to match the ones used during training.)

This should produce an execution directory (in `state/execs/` by default)
with a log file in it. Then run

    ./tables/log-parsers/get-predictions.py path/to/log > predictions
    ./lib/data/WikiTableQuestions/evaluator.py -t ./lib/data/WikiTableQuestions/tagged-data predictions

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
