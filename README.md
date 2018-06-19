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
mvn clean install
```

## Word2Vec Model

Small model for parser has to be manually downloaded from [this link](https://drive.google.com/uc?export=download&confirm&id=1LVOKk7KnDIJphkHRZDa5fDm7ffIcS_Yz) and put it in `resources_nlu/word2vec`.
Other models:
[google_model](https://s3.amazonaws.com/dl4j-distribution/GoogleNews-vectors-negative300.bin.gz)

# Run

Running the NLU module requires resources that are currently stored in the `roboy_dialog` parent repository. Please run the parser from a directory that contains a `resources_nlu` subfolder and a `parser.properties` file with all the required runtime resources.

From such a folder, the following command may be used to start the parser:

```bash
java -Xmx6g -d64 -cp \
    nlu/parser/target/roboy-parser-2.0.0-jar-with-dependencies.jar \
    edu.stanford.nlp.sempre.roboy.SemanticAnalyzerInterface.java
```
