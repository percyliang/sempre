# SEMPRE 2.4: Semantic Parsing with Execution

## What is semantic parsing?

A semantic parser maps natural language utterances into an intermediate logical
form, which is "executed" to produce a denotation that is useful for some task.

A simple arithmetic task:

- Utterance: *What is three plus four?*
- Logical form: `(+ 3 4)`
- Denotation: `7`

A question answering task:

- Utterance: *Where was Obama born?*
- Logical form: `(place_of_birth barack_obama)`
- Denotation: `Honolulu`

A virtual travel agent task:

- Utterance: *Show me flights to Montreal leaving tomorrow.*
- Logical form: `(and (type flight) (destination montreal) (departure_date 2014.12.09))`
- Denotation: `(list ...)`

By parsing utterances into logical forms, we obtain a rich representation that
enables much deeper, context-aware understanding beyond the words.  With the
rise of natural language interfaces, semantic parsers are becoming increasingly
more powerful and useful.

## What is SEMPRE?

SEMPRE is a toolkit that makes it easy to develop semantic parsers for new
tasks.  The main paradigm is to learn a feature-rich discriminative semantic
parser from a set of utterance-denotation pairs.  One can also quickly
prototype rule-based systems, learn from other forms of supervision, and
combine any of the above.

If you use SEMPRE in your work, please cite:

    @inproceedings{berant2013freebase,
      author = {J. Berant and A. Chou and R. Frostig and P. Liang},
      booktitle = {Empirical Methods in Natural Language Processing (EMNLP)},
      title = {Semantic Parsing on {F}reebase from Question-Answer Pairs},
      year = {2013},
    }

SEMPRE has been used in the following papers:

- J. Berant and A. Chou and R. Frostig and P. Liang. [Semantic parsing on
  Freebase from question-answer
  pairs](http://cs.stanford.edu/~pliang/papers/freebase-emnlp2013.pdf).  EMNLP,
  2013.
  This paper introduced SEMPRE 1.0, applied it to question answering on
  Freebase, and created the WebQuestions dataset.  The paper focuses on scaling
  up semantic parsing via alignment and bridging, and does not talk about the
  SEMPRE framework at all.  To reproduce those results, check out SEMPRE 1.0.
- J. Berant and P. Liang.  [Semantic Parsing via
  Paraphrasing](http://cs.stanford.edu/~pliang/papers/paraphrasing-acl2014.pdf).
  ACL, 2014.
  This paper also used SEMPRE 1.0.  The paraphrasing model is somewhat of a
  offshoot, and does not use many of the core learning and parsing utiltiies in
  SEMPRE.  To reproduce those results, check out SEMPRE 1.0.

Please refer to the [project page](https://nlp.stanford.edu/software/sempre/) for a more complete list.

## Where do I go next?

- If you're new to semantic parsing, you can learn more from the [background
  reading section of the tutorial](TUTORIAL.md).
- Install SEMPRE using the instructions under **Installation** below.
- Walk through the [tutorial](TUTORIAL.md)
  to get a hands-on introduction to semantic parsing through SEMPRE.
- Read the complete [documentation](DOCUMENTATION.md)
  to learn about the different components in SEMPRE.

# Installation

## Requirements

You must have the following already installed on your system.

- Java 8 (not 7)
- Ant 1.8.2
- Ruby 1.8.7 or 1.9
- wget
- make (for compiling fig and Virtuoso)
- zip (for unzip downloaded dependencies)

Other dependencies will be downloaded as you need them.  SEMPRE has been tested
on Ubuntu Linux 12.04 and MacOS X.  Your mileage will vary depending on how
similar your system is.

## Easy setup

1. Clone the GitHub repository:

        git clone https://github.com/percyliang/sempre

2. Download the minimal core dependencies (all dependencies will be placed in `lib`):

        ./pull-dependencies core

3. Compile the source code (this produces `libsempre/sempre-core.jar`):

        ant core

4. Run an interactive shell:

        ./run @mode=simple

    You should be able to type the following into the shell and get the answer `(number 7)`:

        (execute (call + (number 3) (number 4)))

To go further, check out the [tutorial](TUTORIAL.md) and then the [full
documentation](DOCUMENTATION.md).

## Virtuoso graph database

If you will be using natural language to query databases (e.g., Freebase), then
you will also need to setup your own Virtuoso database (unless someone already
has done this for you):

For Ubuntu, follow this:

    sudo apt-get install -y automake gawk gperf libtool bison flex libssl-dev

    # Clone the repository
    ./pull-dependencies virtuoso

    # Make and install
    cd virtuoso-opensource
    ./autogen.sh
    ./configure --prefix=$PWD/install
    make
    make install
    cd ..

on OS/X you can install virtuoso using homebrew by following the instructions
[here](http://carsten.io/virtuoso-os-on-mac-os/)

To have SEMPRE interact with Virtuoso, the required modules need to be compiled as follow:

    ./pull-dependencies core corenlp freebase
    ant freebase

# Contribute

To contribute code or resource to SEMPRE:

- Create a fork of the repository. If you already have a fork,
  it is a good idea to sync with the upstream repository first.
- Push your changes to a new branch in your fork.
- Start a pull request: go to your branch on the GitHub website,
  then click "New pull request". Please specify the `develop` branch
  of the upstream repository.

# ChangeLog

Changes from SEMPRE 1.0 to SEMPRE 2.0:

- Updated tutorial and documentation.
- Refactored into a core part for building semantic parsers in general;
  interacting with Freebase and Stanford CoreNLP are just different modules.
- Removed fbalignment (EMNLP 2013) and paraphrase (ACL 2014) components to
  avoid confusion.  If you want to reproduce those systems, use SEMPRE 1.0.

Changes from SEMPRE 2.0 to SEMPRE 2.1:

- Added the `tables` package for the paper *Compositional semantic parsing on semi-structured tables* (ACL 2015).
- Add and `overnight` package for the paper *Building a semantic parser overnight* (ACL 2015).

Changes from SEMPRE 2.1 to SEMPRE 2.2:

- Added code for the paper *Inferring Logical Forms From Denotations* (ACL 2016).

Changes from SEMPRE 2.2 to SEMPRE 2.3:

- Added the `interactive` package for the paper *Naturalizing a programming language through interaction* (ACL 2017).

Changes from SEMPRE 2.3 to SEMPRE 2.3.1:

- Modified the `tables` module to resemble SEMPRE 2.1, effectively making it work again.

Changes from SEMPRE 2.3.1 to SEMPRE 2.4:

- Added the `cprune` package for the paper *Macro Grammars and Holistic Triggering for Efficient Semantic Parsing* (EMNLP 2017).
