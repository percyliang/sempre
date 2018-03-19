# ROBOY SEMANTIC PARSER

Semantic Parser based on [SEMPRE](https://nlp.stanford.edu/software/sempre/).

# Installation

## Requirements

You must have the following already installed on your system.

- Java 8 (not 7)
- Maven
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
./pull-dependencies roboy
mvn clean
mvn install
```

# Word2Vec Model
Small model for parser has to be manually downloaded from [this link](https://drive.google.com/uc?export=download&confirm&id=1LVOKk7KnDIJphkHRZDa5fDm7ffIcS_Yz) and put it in data/word2vec.
Other models:
[google_model](https://s3.amazonaws.com/dl4j-distribution/GoogleNews-vectors-negative300.bin.gz)

# Run

## Roboy Talk Grammar and Lexicon

To run SEMPRE with Roboy grammar in interactive mode (from command line):
```
mvn exec:java@interactive -Dexec.mainClass=edu.stanford.nlp.sempre.Main
```

To run SEMPRE with Roboy grammar in socket mode (port 5000):
```
mvn exec:java@demo -Dexec.mainClass=edu.stanford.nlp.sempre.Main
```
To run SEMPRE with Roboy grammar in socket mode (port 5000) with training:
```
mvn exec:java@test -Dexec.mainClass=edu.stanford.nlp.sempre.Main
```

To run SEMPRE with Roboy grammar in web server mode:
```
mvn exec:java@debug -Dexec.mainClass=edu.stanford.nlp.sempre.Main
```
