# SEMPRE: Semantic Parsing with Execution

SEMPRE is a toolkit for training semantic parsers, which map natural language
utterances to denotations (answers) via intermediate logical forms.  See
TUTORIAL.md for a walkthrough of the system.

If you use this system, please cite:

    @inproceedings{berant2013freebase,
      author = {J. Berant and A. Chou and R. Frostig and P. Liang},
      booktitle = {Empirical Methods in Natural Language Processing (EMNLP)},
      title = {Semantic Parsing on {F}reebase from Question-Answer Pairs},
      year = {2013},
    }

# Requirements

SEMPRE depends on the following:

* Java 7
* Ruby (version 1.8.7 or 1.9)
* [fig](https://github.com/percyliang/fig)
* [Google Guava](https://code.google.com/p/guava-libraries/) (version 14)
* [Jackson JSON Processor](http://wiki.fasterxml.com/JacksonHome) (version 2.2)
* [TestNG](http://testng.org/) (version 6.8)
* [Apache Lucene](http://lucene.apache.org/) (version 4.4)
* [Stanford CoreNLP](http://nlp.stanford.edu/software/corenlp.shtml) (version 3.2)

Aside from Java and Ruby, remaining software dependencies are among
what's fetched by the `download-depenencies` script.

# Tests

To troubleshoot or check for repository health, you can run a suite of unit
tests that come packaged with the system.  The command for this is:

    ./sempre @mode=test -excludegroups xfail

These tests assume you have a connection to a SPARQL endpoint at localhost:3093.
To avoid this assumption, you can exclude any tests that require a SPARQL
server by instead running:

    ./sempre @mode=test -excludegroups xfail,sparql

And if you don't have emnlp2013 dependencies downloaded, you'll need to exclude
yet another group of unit tests:

    ./sempre @mode=test -excludegroups xfail,sparql,emnlp2013

All of these tests should pass.

# License

SEMPRE is licensed under the [GNU General Public
License](http://www.gnu.org/licenses/gpl-2.0.html) (v2 or later).
Note that this is the /full/ GPL, which allows many free uses, but not
its use in distributed [proprietary
software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem).
