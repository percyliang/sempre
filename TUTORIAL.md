# SEMPRE 2.0 tutorial

In this tutorial, we will provide a brief tour of SEMPRE.  This tutorial is
very much about the mechanics of the system, not about the linguistics or
semantic parsing from a research point of view (for those, see the recommended
readings at the end of this document).  Once you have gone through the tutorial,
you can read the [full documentation](DOCUMENTATION.md).

We will construct a semantic parser to understand a toy subset of natural
language.  Concretely, the system we will build will have the following
behavior:

- Input: *What is three plus four?*
- Output: 7

Recall that in semantic parsing, *natural language utterances* are mapped into
*logical forms* (think programs), which are executed to produce some
*denotation* (think return value).

We have assumed you have already [installed](README.md#installation)
SEMPRE and can open up a shell:

    ./run @mode=simple

This will put you in an interactive prompt where you can develop a system and
parse utterances into tiny Java programs.  Note: you might find it convenient
to use `rlwrap` to get readline support.

Just to provide a bit of transparency: The `run` script simply creates a
shell command and executes it.  To see which command is run, do:

    ./run @mode=simple -n

This should print out:

    java -cp libsempre/*:lib/* -ea edu.stanford.nlp.sempre.Main -Main.interactive

You can pass in additional options:

    ./run @mode=simple -Parser.verbose 3  # Turn on more verbose debugging for the parser
    ./run @mode=simple -help              # Shows all options and default values

## Section 1: Logical forms and denotations

A **logical form** (class `Formula` in SEMPRE) is a hierarchical expression.
In the base case, we have primitive logical forms for representing concrete
values (booleans, numbers, strings, dates, names, and lists):

    (boolean true)
    (number 3)
    (string "hello world")
    (date 2014 12 8)
    fb:en.barack_obama
    (list (number 1) (number 2))

Logical forms can be constructed recursively using `call`, which takes a
function name followed by arguments, which are themselves logical forms:

    (call + (number 3) (number 4))
    (call java.lang.Math.cos (number 0))
    (call .indexOf (string "what is this?") (string is))
    (call .substring (string "what is this?") (number 5) (number 7))
    (call if (call < (number 3) (number 4)) (string yes) (string no))

In general:

    (call <function-name> <logical-form-argument-1> ... <logical-form-argument-n>)

Later, we will see other ways (besides `call`) of building more complex logical
forms from simpler logical forms.

Note that each logical form is painfully explicit about types.  You would
probably not want to program directly in this language (baby Java using
LISP-like notation), but that is not the point; we will later generate these
logical forms automatically from natural language.

We can execute a logical form by typing the following into the interactive
prompt:

    (execute (call + (number 3) (number 4)))

In general:

    (execute <logical-form>)

This should print out `(number 7)`, which we refer to as the *denotation*
(class `Value` in SEMPRE) of the logical form.  Try to execute the other
logical forms and see what you get.

**Exercise 1.1**: write a logical form that computes the first word
("compositionality") in the string "compositionality is key".

### Lambda expressions

So far, we have been representing logical forms that produce a single output
value (e.g., "compositionality").  But one of the key ideas of having programs
(logical forms) is the power of abstraction &mdash; that we can represents
*functions* that compute an output value for each input value.

For example, the following logical form denotes a function that takes a number
and returns its square:

    (lambda x (call * (var x) (var x)))

If you execute this logical form directly, you will get an error, because the
denotation of this logical form is a function, which is not handled by the
`JavaExecutor`.  However, we can apply this function to an argument `(number
3)`:

    ((lambda x (call * (var x) (var x))) (number 3))

This logical form now denotes a number.  Executing this logical form should
yield `(number 9)`.

In general:

    (<function-logical-form> <argument-logical-form>)

**Exercise 1.2**: Adapt your logical form form Exercise 1.1 to compute the
first word of any string.  Your answer should be `(lambda x ...)`.  Create a
logical form that applies this on the argument `(string "compositionality is
key")`.

Technical note: these lambda expressions are actually just doing macro
substitution, not actually representing higher-order functions; since there are
no side effects here, there is no difference.

This concludes the section on logical forms and denotations.  We have presented
one system of logical forms, which are executed using `JavaExecutor`.  The
system supports other types of logical forms, for example, those which encode
SPARQL database queries for question answering (we will get to that later).
Note that there is no mention of natural language yet...

## Section 2: Parsing utterances to logical forms

Having established the nature of logical forms and their denotations, let us
turn to the problem of mapping a natural language utterance into a logical
form.  Again, the key framework is *compositionality*, which roughly says that
the meaning of a full sentence is created by combining the meanings of its
parts.  For us, meanings are represented by logical forms.

We will start by defining a **grammar** (class `Grammar` in SEMPRE), which is a
set of **rules** (class `Rule` in SEMPRE), which specify how to combine logical
forms to build more complex ones in a manner that is guided by the natural
language.

We will run through some examples to give you a feel for how things work,
and then go into the details.  First, let us add a rule to the grammar by
typing the following into the interactive prompt:

    (rule $ROOT (three) (ConstantFn (number 3)))

Now to parse an utterance, just type it in to the interactive prompt:

    three

The parser should print out (among other information) a line that shows that
the utterance was parsed successfully into a **derivation** (class `Derivation`
in SEMPRE), which importantly carries the correct logical form `(number 3.0)`:

    (derivation (formula (number 3.0)) (value (number 3.0)) (type fb:type.number))

Now type in the utterance:

    four

You should get no results because no rule matches `four`.  To fix that, let
us create a more general rule:

    (rule $ROOT ($PHRASE) (NumberFn))

This rule says for any phrase (sequence of consecutive tokens), pass it to a special
function called `NumberFn`, which will transform the phrase string into a new
derivation representing a number.

Now, you can parse the following:

    four
    20

Note: if you now type in `three`, you should get two derivations that yield the
same answer, one coming from each rule.  Note that `twenty-five million` will
not parse because we are using `SimpleLanguageAnalyzer`.  Later, we can using
Stanford CoreNLP to improve the basic linguistic capabilities.

So far, we have only parsed utterances using one rule, but the true power of
these grammars come from combining multiple rules.  Copy and paste in the
following rules:

    (rule $Expr ($PHRASE) (NumberFn))
    (rule $Operator (plus) (ConstantFn (lambda y (lambda x (call + (var x) (var y))))))
    (rule $Operator (times) (ConstantFn (lambda y (lambda x (call * (var x) (var y))))))
    (rule $Partial ($Operator $Expr) (JoinFn forward))
    (rule $Expr ($Expr $Partial) (JoinFn backward))
    (rule $ROOT ((what optional) (is optional) $Expr (? optional)) (IdentityFn))

Now try typing in:

    What is three plus four?

The output should be:

    (number 7)

We can parse longer sentences.  Type in:

    What is three plus four times two?

There should be two derivations, yielding `(number 14)` and `(number 11)`,
corresponding to either combining *three plus four* first or *four times two*
first.  Note that this is expected because we have not encoded any order of
operations anywhere.

Hopefully that should give you a sense of what parsing looks like.  Let us now
take a closer look.  At the end of the day, a grammar declaratively specifies a
mapping from utterances to a set of candidate derivations.  A **parser** (class
`Parser` in SEMPRE) is an actual algorithm that takes the grammar and generates
those derivations.  Recall that a derivation looks like this:

    (derivation (formula (number 3.0)) (value (number 3.0)) (type fb:type.number))

Formally, each derivation produced by the parser has the following properties:

1. Span i:j (e.g., 0:1): specifies the contiguous portion of the input
   utterance (tokens i to j-1) that the Derivation is constructed from.
2. Category (e.g., `$ROOT`): categories place hard constraints on what
   Derivations can be combined.
3. Type (e.g., `(fb:type.number)`): typically more fine-grained than the
   category, and is generated dynamically.
4. Logical form (e.g., `(call + (number 3) (number 4))`): what we normally
   think of as the output of semantic parsing.
5. Value (e.g., `(number 7)`): the result of executing the logical form.

There are some special categories:

1. `$TOKEN`: matches a single token of the utterance.  Formally, the parser
   builds a Derivation with category `$TOKEN` and logical form corresponding to
   the token (e.g., `(string three)`) for each token in the utterance.
2. `$PHRASE`: matches any contiguous subsequence of tokens.  The logical form created
   is the concatenation of those tokens (e.g., `(string "twenty-five million")`).
3. `$LEMMA_TOKEN`: like `$TOKEN`, but the logical form produced is a lemmatized
   version of the token (for example $TOKEN would yield *cows*, while
   $LEMMA_TOKEN would yield *cow*).
4. `$LEMMA_PHRASE`: the lemmatized version of `$PHRASE`.
5. `$ROOT` Derivations that have category `$ROOT` and span the entire utterance
   are executed, scored, and sent back to the user.

Now let us see how a grammar specifies the set of derivations.
A grammar is a set of rules, and each rule has the following form:

    (rule <target-category> (<source-1> ... <source-k>) <semantic-function>)

1. Target category (e.g., `$ROOT`): any derivation produced by this rule is
   labeled with this category.  `$ROOT` is the designated top-level category.
   Derivations of type `$ROOT` than span the entire utterance are returned to the
   user.
2. Source sequence (e.g., `three`): in general, this is a sequence of tokens and categories
   (all categories start with `$` by convention).  Tokens (e.g., `three`) are
   matched verbatim, and categories (e.g., `$PHRASE`) match any derivation that
   is labeled with that category and has a span at that position.
3. Semantic function (`SemanticFn`): a semantic function takes a sequence of
   derivations corresponding to the categories in the children and produces a set
   of new derivations which are to be labeled with the target category.
   Semantic functions run arbitrary Java code, and allow the parser to integrate
   custom logic in a flexible modular way.  In the example above, `ConstantFn`
   is an example of a semantic function which always returns one derivation
   with the given logical form (e.g., `(number 3)`).  `JoinFn` produces a
   derivation whose logical form is the composition of the logical forms of the
   two source derivations.

Derivations are built recursively: for each category and span, we construct a
set of Derivations.  We can apply a rule if there is some segmentation of the
span into sub-spans $s_1, \dots, s_k$ and a derivation $d_i$ on each span $s_i$
with category |source_i|.  In this case, we pass the list of derivations as
input into the semantic function.  The output is a set of derivations (possibly
zero).

The first rule is a familiar one that just parses strings such as *three*
into the category `$Expr`:

    (rule $Expr ($PHRASE) (NumberFn))

Specifically, one derivation with logical form `(string three)` is created
with category `$PHRASE` and span 0:1.  This derivation is passed into
`NumberFn`, which returns one derivation with logical form `(number 3)` and
category `$Expr` and span 0:1.  The same goes for *four* on span 2:3.

The next two rules map the tokens *plus* and *times* to a static logical form
(returned by `ConstantFn`):

    (rule $Operator (plus) (ConstantFn (lambda y (lambda x (call + (var x) (var y))))))
    (rule $Operator (times) (ConstantFn (lambda y (lambda x (call * (var x) (var y))))))

The next two rules are the main composition rules:

    (rule $Partial ($Operator $Expr) (JoinFn forward))
    (rule $Expr ($Expr $Partial) (JoinFn backward))

The semantic function `(JoinFn forward)` takes two a lambda term `$Operator`
and an argument `$Expr` and returns a new derivation by forward application:

    Source $Operator: (lambda y (lambda x (call + (var x) (var y))))
    Source $Expr: (number 4)
    Target $Partial: (lambda x (call + (var x) (number 4)))

The semantic function `(Join backward)` takes an argument `$Expr` and a lambda
term `$Partial` and returns a new derivation by backward application:

    Source $Expr: (number 3)
    Source $Partial: (lambda x (call + (var x) (number 4)))
    Target $Expr: (call + (number 3) (number 4))

    (rule $ROOT ((what optional) (is optional) $Expr (? optional)) (IdentityFn))

We allow some RHS elements to be optional, so that we could have typed in
`three plus four` or `three plus four?`.  `IdentityFn` simply takes the logical
form corresponding to `$Expr` and passes it up.

The complete derivation for *three plus four* is illustrated here:

                              $ROOT : (call + (number 3) (number 4)))
                                | [IdentityFn]
                              $Expr : (call + (number 3) (number 4)))
                                | [JoinFn backward]
      +-------------------------+-------------------------+
      |                                                   |
      |                                               $Partial : (lambda x (call + (var x) (number 4)))
      |                                                   | [JoinFn forward]
      |                     +-----------------------------+------------------------------+
      |                     |                                                            |
    $Expr : (number 3)  $Operator : (lambda y (lambda x (call + (var x) (var y))))     $Expr : (number 4)
      | [NumberFn]          | [ConstantFn]                                               | [NumberFn]
    $PHRASE: three          |                                                          $PHRASE : four
      | [built-in]          |                                                            | [built-in]
    three                  plus                                                         four


**Exercise 2.1**: write rules that can parse the following utterances into
into the category `$Expr`:

    length of hello world         # 11
    length of one                 # 3

Your rules should look something like:

    (rule $Function (length of) ...)
    (rule $Expr ($Function $PHRASE) ...)

**Exercise 2.2**: turn your "first word" program into a rule so that you can
parse the following utterances into `$String`:

    first word in compositionality is key       # compositionality
    first word in a b c d e                     # a

**Exercise 2.3**: combine all the rules that you have written to produce one grammar
that can parse the following:

    two times length of hello world             # 22
    length of hello world times two             # (what happens here?)

To summarize, we have shown how to connect natural language utterances and
logical forms using grammars, which specify how one can compositionally form
the logical form incrementally starting from the words in the utterance.  Note
that we are dealing with grammars in the computer science sense, not in the
linguistic sense, as we are not developing a linguistic theory of
grammaticality; we are merely trying to parse some useful subset of utterances
for some task.  Given an utterance, the grammar defines an entire set of
derivations, which reflect both the intrinsic ambiguity of language as well as
the imperfection of the grammar.  In the next section, we will show how to
learn a semantic parser that can resolve these ambiguities.

### Saving to a file (optional)

You can put a set of grammar rules in a file (e.g.,
`data/tutorial-arithmetic.grammar`) and load it:

    ./run @mode=simple -Grammar.inPaths data/tutorial-arithmetic.grammar

If you edit the grammar, you can reload the grammar without exiting the
prompt by typing:

    (reload) 

### Using CoreNLP (optional)

Recall that we were able to parse *four*, but not *twenty-five million*,
because we used the `SimpleLanguageAnalyzer`.  In this section, we will show
how to leverage Stanford CoreNLP, which provides us with more sophisticated
linguistic processing on which we can build more advanced semantic parsers.

First, we need to do download an additional dependency (this could take a while
to download because it loads all of the Stanford CoreNLP models for
part-of-speech tagging, named-entity recognition, syntactic dependency parsing,
etc.):

    ./pull-dependencies corenlp

Compile it:

    ant corenlp

Now we can load the SEMPRE interactive shell with `CoreNLPAnalyzer`:

    ./run @mode=simple -languageAnalyzer corenlp.CoreNLPAnalyzer -Grammar.inPaths data/tutorial-arithmetic.grammar

The following utterances should work now (the initial utterance will take a few
seconds while CoreNLP models are being loaded):

    twenty-five million
    twenty-five million plus forty-two

## Section 3: Learning

So far, we have used the grammar to generate a set of derivations given an
utterance.  We could work really hard to make the grammar not overgenerate, but
this will in general be hard to do without tons of manual effort.  So instead, we will
use machine learning to learn a model that can choose the best derivation (and
thus logical form) given this large set of candidates.  So the philosophy is:

- Grammar: small set of manual rules, defines the candidate derivations
- Learning: automatically learn to pick the correct derivation using features

In a nutshell, the learning algorithm (class `Learner` in SEMPRE) uses
stochastic gradient descent to optimize the conditional log-likelihood of the
denotations given the utterances in a training set.  Let us unpack this.

### Components of learning

First, for each derivation, we extract a set of **features** (formally a map
from strings to doubles &mdash; 0 or 1 for indicator features) using a feature
extractor (class `FeatureExtractor` in SEMPRE), which is an arbitrary function
on the derivation.  Given a parameter vector theta (class `Params` in SEMPRE),
which is also a map from strings to doubles, the inner product gives us a
score:

    Score(x, d) = features(x, d) dot theta,

where x is the utterance and d is a candidate derivation.

Second, we define a **compatibility function** (class `ValueEvaluator` in SEMPRE)
between denotations, which returns a number between 0 and 1.  This allows us
learn with approximate values (e.g., "3.5 meters" versus "3.6 meters") and
award partial credit.

Third, we have a dataset (class `Dataset` in SEMPRE) consisting of **examples**
(class `Example` in SEMPRE), which specifies utterance-denotation pairs.
Datasets can be loaded from files; here is what
`data/tutorial-arithmetic.grammar` looks like:

    (example
      (utterance "three and four")
      (targetValue (number 7))
    )

Intuitively, the learning algorithm will tune the parameter vector theta so
that derivations with logical forms whose denotations have high compatibility
with the target denotation are assigned higher scores.  For the mathematical
details, see the learning section of this
[paper](http://www.stanford.edu/~cgpotts/manuscripts/liang-potts-semantics.pdf).

### No learning

As a simple example, imagine that a priori, we do not know what the word *and*
means: it could be either plus or times.  Let us add two rules to capture the
two possibilities (this is reflected in `data/tutorial-arithmetic.grammar`):

    (rule $Operator (and) (ConstantFn (lambda y (lambda x (call * (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))))
    (rule $Operator (and) (ConstantFn (lambda y (lambda x (call + (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))))

Start the interactive prompt:

    ./run @mode=simple -Grammar.inPaths data/tutorial-arithmetic.grammar

and type in:

    three and four

There should be two derivations each with probability 0.5 (the system arbitrarily chooses one):

    (derivation (formula (((lambda y (lambda x (call * (var x) (var y)))) (number 4.0)) (number 3.0))) (value (number 12.0)) (type fb:type.number)) [score=0, prob=0.500]
    (derivation (formula (((lambda y (lambda x (call + (var x) (var y)))) (number 4.0)) (number 3.0))) (value (number 7.0)) (type fb:type.number)) [score=0, prob=0.500]

### Batch learning

To perform (batch) learning, we run SEMPRE:

    ./run @mode=simple -Grammar.inPaths data/tutorial-arithmetic.grammar -FeatureExtractor.featureDomains rule -Dataset.inPaths train:data/tutorial-arithmetic.examples -Learner.maxTrainIters 3

The `rule` feature domain tells the feature extractor to increment the feature
each time the grammar rule is applied in the derivation.  `Dataset.inPaths`
specifies the examples file to train on, and `-Learner.maxTrainIters 3`
specifies that we will iterate over all the examples three times.

Now type:

    three and four

The correct derivation should now have much higher score and probability:

    (derivation (formula (((lambda y (lambda x (call + (var x) (var y)))) (number 4)) (number 3))) (value (number 7)) (type fb:type.any)) [score=18.664, prob=0.941]
    (derivation (formula (((lambda y (lambda x (call * (var x) (var y)))) (number 4)) (number 3))) (value (number 12)) (type fb:type.any)) [score=15.898, prob=0.059]

You will also see the features that are active for the predicted derivation.
For example, the following line represents the feature indicating that we
applied the rule mapping *and* to `+`:

    [ rule :: $Operator -> and (ConstantFn (lambda y (lambda x (call + (var x) (var y))))) ]  1.383 = 1 * 1.383

The feature value is 1, the feature weight is 1.383, and their product is the
additive contribution to the score of this derivation.  You can look at the score of the other derivation:

    (select 1)

The corresponding feature there is:

    [ rule :: $Operator -> and (ConstantFn (lambda y (lambda x (call * (var x) (var y))))) ] -1.383 = 1 * -1.383

This negative contribution to the score is why we favored the `+` derivation
over this `*` one.

We can also inspect the parameters:

    (params)

### Online learning

Finally, you can also do (online) learning directly in the prompt:

    (accept 1)

This will accept the `*` derivation as the correct one and update the
parameters on the fly.  If you type:

    three and four

again, you will see that the probability of the `+` derivation has decreased.
If you type `(accept 1)` a few more times, the `*` derivation will dominate
once more.

## Section 4: Lambda DCS and SPARQL

So far, we used `JavaExecutor` to map logical forms to denotations by executing
Java code.  A major application of semantic parsing (and indeed the initial one
that gave birth to SEMPRE) is where the logical forms are database queries.  In
this section, we will look at querying graph databases.

A graph database (e.g., Freebase) stores information about entities
and their properties; concretely, it is just a set of triples $(s, p, o)$,
where $s$ and $o$ are entities and $p$ is a property.  For example:

    fb:en.barack_obama fb:place_of_birth fb:en.honolulu

is one triple.  If we think of the entities as nodes in a directed graph, the
each triple is a directed edge between two nodes labeled with the property.

See `freebase/data/tutorial.ttl` for an example of a tiny subset of the Freebase graph
pertaining to geography about California.

First, pull the dependencies needed for Freebase:

    ./pull-dependencies freebase

### Setting up your own Virtuoso graph database

We use the graph database engine, Virtuoso, to store these triples and allow
querying.  Follow these instructions if you want to create your own Virtuoso instance.

First, make sure you have Virtuoso installed &mdash; see the Installation
section of the [readme](README.md).

Then start the server:

    freebase/scripts/virtuoso start tutorial.vdb 3001

Add a small graph to the database:

    freebase/scripts/virtuoso add freebase/data/tutorial.ttl 3001

Now you can query the graph (this should print out three items):

    ./run @mode=query @sparqlserver=localhost:3001 -formula '(fb:location.location.containedby fb:en.california)'

To stop the server:

    freebase/scripts/virtuoso stop 3001

### Setting up a copy of Freebase

The best case is someone already installed Freebase for you and handed you a
host:port.  Otherwise, to run your own copy of the entire Freebase graph (a
2013 snapshot), read on.

Download it (this is really big and takes a LONG time):

    ./pull-dependencies fullfreebase-vdb

Then you can start the server (make sure you have at least 60GB of memory):

    freebase/scripts/virtuoso start lib/fb_data/93.exec/vdb 3093

### Lambda DCS

SPARQL is the standard language for querying graph databases, but it will be
convenient to use a language more tailored for semantic parsing.  We will use
[lambda DCS](http://arxiv.org/pdf/1309.4408.pdf), which is based on a mix
between lambda calculus, description logic, and dependency-based compositional
semantics (DCS).

We assume you have started the Virtuoso database:

    freebase/scripts/virtuoso start tutorial.vdb 3001

Then start up a prompt:

    ./run @mode=simple-freebase-nocache @sparqlserver=localhost:3001

The simplest logical formula in lambda DCS is a single entity such as `fb:en.california`.
To execute this query, simply type the following into the interactive prompt:

    (execute fb:en.california)

This should return:

    (list (name fb:en.california California))

The result is a list containing the single entity.  Here, `fb:en.california` is
the canonical Freebase ID (always beginning with the prefix `fb:`) and
`California` is the name (look at `data/tutorial.ttl` to see where this comes
from).

Let us try a more complex query which will fetch all the cities (in the database);

    (execute (fb:type.object.type fb:location.citytown))

This should return the three cities, Seattle, San Francisco, and Los Angeles.
We can restrict to *cities in California*:

    (execute (and (fb:type.object.type fb:location.citytown) (fb:location.location.containedby fb:en.california)))

This should return the two cities satisfying the restriction: San Francisco and Los Angeles.

We can count the number of cities (should return 3):

    (execute (count (fb:type.object.type fb:location.citytown)))

We can also get the city with the largest area:

    (execute (argmax 1 1 (fb:type.object.type fb:location.citytown) fb:location.location.area))

Now let us take a closer look at what is going on with these logical forms
under the hood.  We are using a logical language called lambda DCS.

Here are the following types of logical forms:

1. Primitive (e.g., `fb:en.seattle`): denotes a set containing that single entity.
1. Intersection `(and |u1| |u2|)`: denotes the intersection of the sets denoted
   by unary logical forms `u1` and `u2`.
1. Join `(|b| |u|)`: denotes the set of $x$ which are connected to some $y$ via
   a binary $b$ and $y$ is in the set denoted by unary $u$.
1. Count `(count |u|)`: denotes the set containing the cardinality of the set denoted by `u`.
1. Superlative `(argmax |rank| |count| |u| |b|)`: sort the elements of `z` by decreasing `b`
   and return `count` elements starting at offset `rank` (1-based).
1. Mu abstraction `(mark (var |v|) |u|)`: same as the unary |u| denoting
   entities |x|, with the exception that |x| must be equal to all occurrences
   of the variable |v| in |u|.
1. Lambda abstraction `(lambda (var |v|) |u|)`: produces a binary (x,y) where
   `x` is in the set denoted by `u` and `y` is the value taken on by variable
   `v`.

See `src/edu/stanford/nlp/sempre/freebase/test/SparqlExecutorTest.java` for
many more examples (which only work on the full Freebase).

**Exercise 4.1**: write lambda DCS logical forms for the following utterances:

    `city with the largest area`

    `top 5 cities by area`

    `countries whose capitals have area at least 500 squared kilometers`

    `states bordering Oregon and Washington`

    `second tallest mountain in France`

    `country with the most number of rivers`

You should familiarize yourself with the [Freebase
schema](http://www.freebase.com/schema) to see which predicates to use.
Execute these on the full Freebase to find out the answer!

### Parsing

So far, we have described the denotations of logical forms for querying a graph
database.  Now we focus on parsing natural language utterances into these
logical forms.

The core challenge is at the lexical level: mapping natural language phrases
(e.g., *born in*) to logical predicates (e.g.,
`fb:people.person.place_of_birth`).  It is useful to distinguish between two
types of lexical items:

- Entities (e.g., `fb:en.barack_obama`): There are generally a huge number of
  entities (Freebase has tens of millions).  Often, string matching gets you
  part of the way there (for example, *Obama* to `fb.en:barack_obama`), but
  there is often quite a bit of ambiguity (Obama is also a city in Japan).

- Non-entities (e.g., `fb:people.person.place_of_birth`), which include unary
  and binary predicates: There are fewer of these, but string matching is
  unlikely to get you very far.

We could always add grammar rules like this:

    (rule $Entity (the golden state) (ConstantFn fb:en.california))
    (rule $Entity (california) (ConstantFn fb:en.california))

but grammars are supposed to be small, so this approach does not scale, so we
are not going to do this.
One way is to create a **lexicon**, which is a mapping from words to predicates
(see `freebase/data/tutorial-freebase.lexicon`), with entries like this:

    {"lexeme": "california", "formula": "fb:en.california"}
    {"lexeme": "the golden state", "formula": "fb:en.california"}
    {"lexeme": "cities", "formula": "(fb:type.object.type fb:location.citytown)"}
    {"lexeme": "towns", "formula": "(fb:type.object.type fb:location.citytown)"}
    {"lexeme": "in", "formula": "fb:location.location.containedby"}
    {"lexeme": "located in", "formula": "fb:location.location.containedby"}

Then we can add the following rules (see
`freebase/data/tutorial-freebase.grammar`):

    (rule $Unary ($PHRASE) (SimpleLexiconFn (type fb:type.any)))
    (rule $Binary ($PHRASE) (SimpleLexiconFn (type (-> fb:type.any fb:type.any))))
    (rule $Set ($Unary) (IdentityFn))
    (rule $Set ($Unary $Set) (MergeFn and))
    (rule $Set ($Binary $Set) (JoinFn forward))
    (rule $ROOT ($Set) (IdentityFn))

The `SimpleLexiconFn` looks up the phrase and returns all formulas that have the given type.  To check the type, use:

    (type fb:en.california)                         # fb:common.topic
    (type fb:location.location.containedby)         # (-> fb:type.any fb:type.any)

`MergeFn` takes the two (unary) logical forms |u| and |v| (in this case, coming from `$Unary` and `$Set`),
and forms the intersection logical form `(and |u| |v|)`.

`JoinFn` takes two logical forms (one binary and one unary) and returns the
logical form `(|b| |v|)`.  Note that before we were using `JoinFn` as function
application.  In lambda DCS, `JoinFn` produces an actual logical form that
corresponds to joining |b| and |v|.  The two bear striking similarities, which
is the basis for the overloading.

Now start the interactive prompt:

    ./run @mode=simple-freebase-nocache @sparqlserver=localhost:3001 -Grammar.inPaths freebase/data/tutorial-freebase.grammar -SimpleLexicon.inPaths freebase/data/tutorial-freebase.lexicon

We should be able to parse the following utterances:

    california
    the golden state
    cities in the golden state
    towns located in california

In general, how does one create grammars?  One good strategy is to start with a
single rule mapping the entire utterance to the final logical form.  Then
decompose the rule into parts.  For example, you might start with:

    (rule $ROOT (cities in california) (ConstantFn (and (fb:type.object.type fb:location.citytown) (fb:location.location.containedby fb:en.california))))

Then you might factor it into two pieces, in order to generalize:

    (rule $ROOT (cities in $Entity) (lambda e (and (fb:type.object.type fb:location.citytown) (fb:location.location.containedby (var e)))))
    (rule $Entity (california) (ConstantFn fb:en.california))

Note that in the first rule, we are writing `(lambda x ...)` directly.  This
means, take the logical form for the source (`$Entity`) and substitute it in
for `x`.

We can refactor the first rule:

    (rule $ROOT ($Unary in $Entity) (lambda x (and (var u) (fb:location.location.containedby (var e)))))
    (rule $Unary (cities) (ConstantFn (fb:type.object.type fb:location.citytown)))

and so on...

**Exercise 4.2**: Write a grammar that can parse the utterances from Exercise
4.1 into a set of candidates containing the true logical form you annotated.
Of course you can trivially write one rule for each example, but try to
decompose the grammars as much as possible.  This is what will permit
generalization.

**Exercise 4.3**: Train a model so that the correct logical forms appear at the
top of the candidate list on the training examples.  Remember to add features.

## Debugging

In the beginning, SEMPRE grammars can be difficult to debug.  This is primarily
because everything is dynamic, which means that minor typos result in empty
results rather than errors.

The first you should do is to check that you do not have typos.  Then, try to
simplify your grammar as much as possible (comment things out) until you have
the smallest example that fails.  Then you should turn on more debugging
output:

Only derivations that reach `$ROOT` over the entire span of the sentence are
built.  You can also turn on debugging to print out all intermediate
derivations so that you can see where something is failing:

    (set Parser.verbose 3)  # or pass -Parser.verbose 3 on the command-line

Often derivations fail because an intermediate combination does not type check.
This option will print out all combinations which are tried.  You might find
that you are combining two logical forms in the wrong way:

    (set JoinFn.verbose 3)
    (set JoinFn.showTypeCheckFailures true)
    (set MergeFn.verbose 3)
    (set MergeFn.showTypeCheckFailures true)

## Appendix: Background reading

So far this tutorial has provided a very operational view of semantic parsing
based on SEMPRE.  The following references provide a broader look at the area
of semantic parsing as well as the linguistic and statistical foundations.

* **Natural language semantics**: The question of how to represent natural
  language utterances using logical forms has been well-studied in linguistics
  under formal (or compositional) semantics.  Start with the
  [CS224U course notes from Stanford](http://www.stanford.edu/class/cs224u/readings/cl-semantics-new.pdf)
  to get a brief taste of the various phenomena in natural language.
  The [Bos/Blackburn book](http://www.let.rug.nl/bos/comsem/book1.html)
  (also see this [related article](http://www.coli.uni-saarland.de/publikationen/softcopies/Blackburn:1997:RIN.pdf))
  gives more details on how parsing to logical forms works (without any
  learning); Prolog code is given too.

* **Log-linear models**: Our semantic parser is based on log-linear models,
  which is a very important tool in machine learning and statistical natural
  language processing.  Start with [a tutorial by Michael
  Collins](http://www.cs.columbia.edu/~mcollins/loglinear.pdf), which is geared
  towards applications in NLP.

* **Semantic parsing**: finally, putting the linguistic insights from formal
  semantics and the computational and statistical tools from machine learning,
  we get semantic parsing.  There has been a lot of work on semantic parsing,
  we will not attempt to list fully here.  Check out the [ACL 2013 tutorial by
  Yoav Artzi and Luke
  Zettlemoyer](http://yoavartzi.com/pub/afz-tutorial.acl.2013.pdf), which
  focuses on how to build semantic parsers using Combinatory Categorical
  Grammar (CCG).  Our [EMNLP 2013
  paper](http://cs.stanford.edu/~pliang/papers/freebase-emnlp2013.pdf) is the
  first paper based on SEMPRE.  This [Annual Reviews
  paper](http://www.stanford.edu/~cgpotts/manuscripts/liang-potts-semantics.pdf)
  provides a tutorial of how to learn a simple model of compositional semantics
  ([Python code](https://github.com/cgpotts/annualreview-complearning) is
  available) along with a discussion of compositionality and generalization.
