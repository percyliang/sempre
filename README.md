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

# Run

Running the NLU module requires resources that are currently stored in the `roboy_dialog` parent repository. Please run it from this repository.
