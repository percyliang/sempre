# ROBOY SEMANTIC PARSER

Semantic Parser based on [SEMPRE](https://nlp.stanford.edu/software/sempre/).

# Installation

## Requirements

You must have the following already installed on your system.

- Java 8 (not 7)
- Ant 1.8.2
- Ruby 1.8.7 or 1.9
- wget
- make (for compiling fig and Virtuoso)
- zip (for unzip downloaded dependencies)

Other dependencies will be downloaded as you need them. SEMPRE has been tested
on Ubuntu Linux 12.04 and MacOS X.  Your mileage will vary depending on how
similar your system is.

## Build

In order to build application with all needed dependencies run:
```
./pull-dependencies core
./pull-dependencies corenlp
./pull-dependencies freebase
./pull-dependencies virtuoso
ant roboy
```

# Run

## Roboy Talk Grammar and Lexicon

To run SEMPRE with Roboy grammar run in interactive mode (from command line):
```
./run @mode=simple -Grammar.inPaths data/roboy-talk.grammar -FeatureExtractor.featureDomains rule -Dataset.inPaths train:data/roboy-talk.examples -Learner.maxTrainIters 10 -languageAnalyzer corenlp.CoreNLPAnalyzer -SimpleLexicon.inPaths data/roboy-talk.lexicon
```

To run SEMPRE with Roboy grammar run in socket mode (port 5000):
```
./run @mode=socket -Grammar.inPaths data/roboy-talk.grammar -FeatureExtractor.featureDomains rule -Dataset.inPaths train:data/roboy-talk.examples -Learner.maxTrainIters 10 -languageAnalyzer corenlp.CoreNLPAnalyzer -SimpleLexicon.inPaths data/roboy-talk.lexicon
```
