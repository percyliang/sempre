WikiTableQuestions Dataset
==========================
Version 0.3 (January 14, 2016)

Introduction
------------

The WikiTableQuestions dataset is for the task of question answering on
semi-structured HTML tables as presented in the paper:

> Panupong Pasupat, Percy Liang.  
>   Compositional Semantic Parsing on Semi-Structured Tables  
>   Association for Computational Linguistics (ACL), 2015.

About TSV Format
----------------

Many files in this dataset are stored as tab-separated values (TSV).
They use the following special constructs:

- List items are separated by `|` (e.g., `when|was|taylor|swift|born|?`).

- The following characters are escaped:
  newline (=> `\n`), backslash (`\` => `\\`), and pipe (`|` => `\p`)
  Note that pipes become `\p` so that doing `x.split('|')` will work.

- Consecutive whitespaces (except newlines) are collapsed into a single space.


Questions and Answers
---------------------

The `data/` directory contains the questions, answers, and the ID of the tables
that the questions are asking about.

Each portion of the dataset is stored as a TSV file where each line contains
one example.

**Field descriptions:**
- id:          unique ID of the example
- utterance:   the question in its original format
- context:     the table used to answer the question
- targetValue: the answer, possibly a `|`-separated list

**Dataset Splits:** We split 22033 examples into multiple sets:

- `training`:
  Training data (14152 examples)

- `pristine-unseen-tables`:
  Test data -- the tables are *not seen* in training data (4344 examples)

- `pristine-seen-tables`:
  Additional data where the tables are *seen* in training data. (3537 examples)
  (Initially intended to be used as development data, this portion of the
  dataset has not been used in any experiment in the paper.)

- `random-split-*`:
  For development, we split `training.tsv` into random 80-20 splits.
  Within each split, tables in the training data (`random-split-seed-*-train`)
  and the test data (`random-split-seed-*-test`) are disjoint.

For our ACL 2015 paper:

- In development set experiments:
  we trained on `random-split-seed-{1,2,3}-train`
  and tested on `random-split-seed-{1,2,3}-test`, respectively.

- In test set experiments:
  we trained on `training` and tested on `pristine-unseen-tables`.

**Supplementary Files:**

- `*.examples` files:
  The LispTree format of the dataset is used internally in our
  [SEMPRE](http://nlp.stanford.edu/software/sempre/) code base.
  The `*.examples` files contain the same information as the TSV files.

Tables
------

The `csv/` directory contains the extracted tables, while the `page/` directory
contains the raw HTML data of the whole web page.

**Table Formats:**

- `csv/xxx-csv/yyy.csv`:
  Comma-separated table (The first row is treated as the column header)
  The escaped characters include:
  double quote (`"` => `\"`) and backslash (`\` => `\\`).
  Newlines are represented as quoted line breaks.

- `csv/xxx-csv/yyy.tsv`:
  Tab-separated table. The TSV escapes explained at the beginning are used.

- `csv/xxx-csv/yyy.table`:
  Human-readable column-aligned table. Some information was loss during
  data conversion, so this format should not be used as an input.

- `csv/xxx-csv/yyy.html`:
  Formatted HTML of just the table

- `page/xxx-page/yyy.html`:
  Raw HTML of the whole web page

- `page/xxx-page/yyy.json`:
  Metadata including the URL, the page title, and the index of the chosen table.
  (Only tables with the `wikitable` class are considered.)

The conversion from HTML to CSV and TSV was done using `table-to-tsv.py`.
Its dependency is in the `weblib/` directory.

CoreNLP Annotated Files
-----------------------
Questions and tables are annotated using CoreNLP 3.5.2.
The annotation is not perfect (e.g., it cannot detect the date "13-12-1989"),
but it is usually good enough.

- `annotated/data/*.annotated`:
  Annotated questions. Each line contains one example.

  Field descriptions:
  - id:          unique ID of the example
  - utterance:   the question in its original format
  - context:     the table used to answer the question
  - targetValue: the answer, possibly a `|`-separated list
  - tokens:      the question, tokenized
  - lemmaTokens: the question, tokenized and lemmatized
  - posTags:     the part of speech tag of each token
  - nerTags:     the name entity tag of each token
  - nerValues:   if the NER tag is numerical or temporal, the value of that
     NER span will be listed here
  - targetCanon: canonical form of the answers where numbers and dates
     are converted into normalized values

- `annotated/xxx-annotated/yyy.annotated`:
  Tab-separated file containing the CoreNLP annotation of each table cell.
  Each line represents one table cell.

  Mandatory fields:
  - row:         row index (-1 is the header row)
  - col:         column index
  - id:          unique ID of the cell.
    - Each header cell gets a unique ID even when the contents are identical
    - Non-header cells get the same ID if they have exactly the same content
  - content:     the cell text (images and hidden spans are removed)
  - tokens:      the cell text, tokenized
  - lemmaTokens: the cell text, tokenized and lemmatized
  - posTags:     the part of speech tag of each token
  - nerTags:     the name entity tag of each token
  - nerValues:   if the NER tag is numerical or temporal, the value of that
     NER span will be listed here

  The following fields are optional:
  - number:      interpretation as a number (for multiple numbers, the first
     number is extracted)
  - date:        interpretation as a date
  - num2:        the second number in the cell (useful for scores like `1-2`)
  - list:        interpretation as a list of items

  Header cells do not have these optional fields.

Version History
---------------

0.3 - Repaired table headers / Added raw HTML tables and CoreNLP annotated data
0.2 - Initial release

For questions and comments, please contact Ice Pasupat <ppasupat@cs.stanford.edu>
