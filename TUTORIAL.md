# SEMPRE: Semantic Parsing with Execution

In this tutorial, we will provide a brief tour of SEMPRE.  This tutorial is
very much about the mechanics of the system, not about the linguistics or
semantic parsing from a research point of view (for those, see the recommended
readings at the end of this document).

We will construct a semantic parser to understand a toy subset of natural
language.  Concretely, the system will have the following behavior:

- Input: *What is three plus four?*
- Output: 7

In semantic parsing, *natural language utterances* are mapped into *logical
forms* (think programs), which are executed to produce some *denotation* (think
return value).

## Configuration

To download all the dependencies for the system, run:

    ./download-dependencies core

To compile the system, run:

    make

To run the system, run:

    java -Xmx3g -cp classes:lib/* edu.stanford.nlp.sempre.Main -executor JavaExecutor -interactive

This will put you in an interactive prompt where you can develop a system and
parse utterances into tiny Java programs (hence `JavaExecutor`).  Note: you
might find it convenient to use `rlwrap` to get readline support.

## Formulas and denotations

A logical form (`Formula`) is a hierarchical expression.  For example, here are
some primitive logical forms:

    (boolean true)
    (number 3)
    (string hello)
    (date 2013 7 28)

Logical forms can be constructed recursively using `call`, which takes a
function name followed by arguments:

    (call + (number 3) (number 4))
    (call java.lang.Math.cos (number 0))
    (call .indexOf (string "what is this?") (string is))
    (call .substring (string "what is this?") (number 5) (number 7))
    (call if (call < (number 3) (number 4)) (string yes) (string no))

Note that each logical form is painfully explicit about types.  You would
probably not want to program directly in this language, but that is not the
point; we will generate these logical forms automatically from natural
language.

We can execute these logical forms by typing into the interactive prompt:

    (execute (call + (number 3) (number 4)))

This should print out `(number 7)`, which we refer to as the *denotation* of
the logical form.  Try to execute the other logical forms and see what you get.

### Lambda expressions

So far, we have been representing monolithic logical forms.  When we start
doing parsing, it will be convenient to be able to refer to logical forms with
some of its parts abstracted out.  For example, the following logical form
represents a function that takes a number and returns its square:

    (lambda x (call * (var x) (var x)))

We apply a lambda expression in the usual Lispy way:

    ((lambda x (call * (var x) (var x))) (number 3))

Executing this expression should yield `(number 9)`.

Technical note: these lambda expressions are just doing macro substitution, not
actually representing higher-order functions; since there are no side effects
here, there is no difference.

This concludes the section on logical forms and denotations.  We have presented
one system of logical forms, which are executed using `JavaExecutor`.  The
system supports other types of logical forms, for example, those which encode
SPARQL database queries for question answering (we will get to that later).
Note that there is no mention of natural language yet...

## Parsing

Having established the nature of logical forms and their denotations, let us
turn to the problem of mapping a natural language utterance into a logical
form.  We will proceed by defining a *grammar*, which is a set of rules
which specify how to perform this mapping piece by piece.  This is a grammar
in the computer science sense, not in the linguistic sense (again, SEMPRE
just provides a framework; you can use whatever grammar you would like).

We will run through some examples to give you a feel for how things work,
and then go into the details.  First, let us add a rule to the grammar by
entering the following into the prompt:

    (rule $ROOT (three) (ConstantFn (number 3)))

Now to parse an utterance, just type it in:

    three

Note: The first time you parse an utterance will be slower since Stanford CoreNLP
components need to be loaded into memory (e.g., the part-of-speech tagger).

The parser should print out (among other information) a line that shows that
the utterance was parsed sucessfully into a *Derivation*:

    (derivation (formula (number 3.0)) (value (number 3.0)) (type fb:type.number))

Now type in the utterance:

    four

You should get no results because no rule will match `four`.  To fix that, let
us create a more general rule:

    (rule $ROOT ($PHRASE) (NumberFn))

This rule says match any phrase (sequence of tokens), pass it to a special
function called `NumberFn`, which will transform the string into a new
derivation.

Now the system can interpret general numbers!  Try typing in:

    twenty five million

The result should be `(number 2.5E7)`.

Note: if you now type in `three`, you should get two derivations that yield the
same answer, one coming from each rule.

So far, we have only parsed utterances using one rule, but the true power of
these grammars come from combining multiple rules.  Copy and paste in the
following rules:

    (rule $Expr ($PHRASE) (NumberFn))
    (rule $Operator (plus) (ConstantFn (lambda y (lambda x (call + (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))))
    (rule $Operator (times) (ConstantFn (lambda y (lambda x (call * (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))))
    (rule $Partial ($Operator $Expr) (JoinFn forward))
    (rule $Expr ($Expr $Partial) (JoinFn backward))
    (rule $ROOT ((what optional) (is optional) $Expr (? optional)) (IdentityFn))

Now try typing in:

    What is three plus four?

The output should be

    (number 7)

We can parse longer sentences.  Type in:

    What is three plus four times two?

There should be two derivations, yielding `(number 14)` and `(number 11)`,
corresponding to either combining *three plus four* first or *four times two*
first.  Note that this is expected because we have not encoded any order of
operations anywhere.

Hopefully that should give you a sense of what parsing looks like.  Let us now
take a closer look.  At the end of the day, a grammar declaratively specifies a
mapping from utterances to a set of candidate *Derivation*s (which are to be
scored and ranked later).  A parser is an actual algorithm that takes the
grammar and generates those derivations.  Recall that a derivation looks like this:

    (derivation (formula (number 3.0)) (value (number 3.0)) (type fb:type.number))

Formally, each Derivation produced by the parser has the following properties:

1. Span i:j (e.g., 0:1): specifies the continguous portion of the input
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

    (rule |target| (|source_1| ... |source_k|) |semantic function|)

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
   Semantic functions are arbitrary Java code, and allow the parser to integrate
   custom logic in a flexible modular way.  In the example above `ConstantFn`
   is an example of a semantic function which always returns one `Derivation`
   with the given logical form (e.g., `(number 3)`).

Derivations are built recursively: for each category and span, we construct a
set of Derivations.  We can apply a rule if there is some segmentation of the
span into sub-spans $s_1, \dots, s_k$ and a derivation $d_i$ on each span $s_i$
with category |source_i|.  In this case, we pass the list of derivations as
input into the semantic function.  The output is a set of derivations (possibly
zero).

The first rule is a familiar one that just parses numbers such as `three
million` into the category `$Expr`:

    (rule $Expr ($PHRASE) (NumberFn))

Specifically, one derivation with logical form `(string three)` is created
with category `$PHRASE` and span 0:1.  This derivation is passed into
`NumberFn`, which returns one derivation with logical form `(number 3)` and
category `$Expr` and span 0:1.  The same goes for *four* on span 2:3.

The next two rules map the tokens *plus* and *times* to a static logical form
(returned by `ConstantFn`):

    (rule $Operator (plus) (ConstantFn (lambda y (lambda x (call + (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))))
    (rule $Operator (times) (ConstantFn (lambda y (lambda x (call * (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))))

Note that the `ConstantFn` specifies both the logical form and the type (note
that the type is a property of the derivation, not of the logical form).  Here,
the logical form is

    (lambda y (lambda x (call + (var x) (var y))))

which represents a curried function that takes `y` and `x` and returns calls
`+` on the values.  The type of this entry is:

    (-> fb:type.number (-> fb:type.number fb:type.number))

The type is used in parsing to prevent bad derivations from being combined
spuriously.

The next two rules are the main composition rules:

    (rule $Partial ($Operator $Expr) (JoinFn forward))
    (rule $Expr ($Expr $Partial) (JoinFn backward))

The semantic function `(JoinFn forward)` takes two a lambda term `$Operator`
and an argument `$Expr` and returns a new derivation by forward application:

    Input $Operator: (lambda y (lambda x (call + (var x) (var y))))
    Input $Expr: (number 4)
    Output $Partial: (lambda x (call + (var x) (number 4)))

The semantic function `(Join backward)` takes an argument `$Expr` and a lambda
term `$Partial` and returns a new derivation by backward application:

    Input $Expr: (number 3)
    Input $Partial: (lambda x (call + (var x) (number 4)))
    Output $Expr: (call + (number 3) (number 4))

    (rule $ROOT ((what optional) (is optional) $Expr (? optional)) (IdentityFn))

We allow some RHS elements to be optional, so that we could have typed in
`three plus four` or `three plus four?`.  `IdentityFn` simply takes the logical
form corresponding to `$Expr` and passes it up.

## Learning

So far, we have used the grammar to generate a set of derivations given an
utterance.  In general, this set is huge and we need a way to rank the
derivations.  Our strategy is to provide the system with training examples,
from which the system can learn a model that places a distribution over
derivations given utterances.

To enable learning in the interactive prompt, do the following (we could have
also passed these in as command-line arguments, e.g.,
`-Master.onlineLearnExamples true -FeatureExtractor.featureDomains rule`):

    (set Master.onlineLearnExamples true)
    (set FeatureExtractor.featureDomains rule)

The first statement turns on online learning updates (this is only necessary
for the interactive prompt).  The second statement adds features that keep
track of which rules we are using.

As a simple example, imagine that a priori, we do not know what the word *and*
means: it could be either plus or times.  Let us add two rules to capture the
two possibilities:

    (rule $Operator (and) (ConstantFn (lambda y (lambda x (call + (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))))
    (rule $Operator (and) (ConstantFn (lambda y (lambda x (call * (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))))

Now type in

    three and four

There should be two derivations each with probability 0.5:

    (derivation (formula (((lambda y (lambda x (call + (var x) (var y)))) (number 4.0)) (number 3.0))) (value (number 7.0)) (type fb:type.number)) [score=0, prob=0.500]
    (derivation (formula (((lambda y (lambda x (call * (var x) (var y)))) (number 4.0)) (number 3.0))) (value (number 12.0)) (type fb:type.number)) [score=0, prob=0.500]

You will also see the features that are active for the first derivation.  For
example, the following feature represents the fact that one of the rules we
just added is active:

    [ rule :: $Operator -> and (ConstantFn (lambda y (lambda x (call + (var x) (var y)))) (-> fb:type.number (-> fb:type.number fb:type.number))) ]      0 = 1 * 0

So far all the features have weight zero, so all the scores (dot products
between feature vector and weight vector) are also zero.

We can add a training example which says that the utterance should map to the first derivation:

    (accept 0)

This should perform a stochastic gradient update of the parameters.  Now type in:

    three and four

The first derivation should have much higher probability (around 0.88).  To see
the new parameters, type in:

    (params)

This concludes the basics of logical forms, denotations, grammars, features,
and learning.  In practice, much of these operations can be done offline by
putting the grammar rules in a file (`data/tutorial.grammar`) and the examples
in another file (`data/tutorial.examples.json).  Then you can one run command
to perform what we just did:

    java -cp classes:lib/* edu.stanford.nlp.sempre.Main -executor JavaExecutor -Grammar.inPaths data/tutorial.grammar -Dataset.inPaths train:data/tutorial.examples.json -readLispTreeFormat false -trainFrac 0.5 -devFrac 0.5 -maxTrainIters 2 -featureDomains rule -execDir tutorial.out

This will create a directory `tutorial.out` which records all the information
associated with this run.

## Lambda DCS and SPARQL

So far, we have worked with `JavaExecutor` as the engine that maps logical
forms to denotations by executing Java code.  A major application of semantic
parsing is where the logical forms are database queries.  In this section, we
will look at querying graph databases.

A graph database (e.g., Freebase) stores information about entities
and their properties; concretely, it is just a set of triples $(s, p, o)$,
where $s$ and $o$ are entities and $p$ is a property.  For example:

    fb:en.barack_obama fb:place_of_birth fb:en.honolulu

is one triple.  If we think of the entities as nodes in a directed graph, the
each triple is a directed edge between two nodes labeled with the property.

See `data/tutorial.ttl` for an example of a tiny subset of the Freebase graph
pertaining to geography about California.

We will use Virtuoso to provide the backend for querying this data.  Make sure
you have Virtuoso downloaded and compiled (see QUICKSTART.md for instructions).

Start the server:

    scripts/virtuoso start tutorial.vdb 3001

Add the small graph to the database:

    scripts/virtuoso add data/tutorial.ttl 3001

Aside: if you want to work with a larger database:

    ./download-dependencies geofreebase_ttl    # Subset of Freebase for geography in .ttl format (not needed to run the server)
    ./download-dependencies geofreebase_vdb    # Virtuoso index for subset of Freebase for geography
    ./download-dependencies fullfreebase_ttl   # All of Freebase in .ttl format (not needed to run the server) [BIG FILE]
    ./download-dependencies fullfreebase_vdb   # Virtuoso index for all of Freebase [BIG FILE]

Then, just replace `tutorial.vdb` with the appropriate `lib/freebase/??.exec/vdb` path.

Now we are ready to make some queries.  Start up the interactive prompt, now with the `SparqlExecutor`:

    java -cp classes:lib/* edu.stanford.nlp.sempre.Main -executor SparqlExecutor -endpointUrl http://localhost:3001/sparql -interactive

SPARQL is a language for querying these graphs, but it will be convenient to
use a language more tailored for semantic parsing which is based on a mix
between lambda calculus, description logic, and dependency-based compositional
semantics.  The simplest formula is a single entity:

    fb:en.california

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

See `SparqlExecutorTest.java` for examples.

### Parsing

So far, we have described the denotations of logical forms for querying a graph
database.  Now we focus on parsing natural language utterances into these
logical forms.

The core challenge is at the lexical level: mapping natural language phrases
(e.g., *born in*) to logical predicates (e.g.,
`fb:people.person.place_of_birth`).  First, we need to download the files that
specify this mapping (roughly, the mappings were created by aligning Freebase
and ClueWeb, as described the EMNLP 2013 paper, but you can just take these
mappings as given for now):

    ./download-dependencies emnlp2013

The following commands will require you having a Virtuoso instance that has all of Freebase indexed.
If you do not have this set up, run the following two commands:

    ./download-dependencies fullfreebase_vdb   # Virtuoso index for all of Freebase [BIG FILE]

    scripts/virtuoso start lib/freebase/93.exec/vdb 3093  # Starts server on localhost at port 3093

Now, restart the SEMPRE shell, pointing the SparqlExecutor to the Virtuoso
instance:

    ./sempre @mode=interact  @domain=webquestions  @sparqlserver=localhost:3093  @cacheserver=local  @load=15 @executeTopOnly=0 -Grammar.inPaths data/empty.grammar

Now, we can add rules with semantic function `LexiconFn`:

    (rule $Entity ($PHRASE) (LexiconFn entity allowInexact))  # e.g., Ulm
    (rule $Unary ($LEMMA_PHRASE) (LexiconFn unary))           # e.g., physicists
    (rule $Binary ($PHRASE) (LexiconFn binary))               # e.g., born in
    (rule $ROOT ($Entity) (IdentityFn))
    (rule $ROOT ($Unary) (IdentityFn))

LexiconFn takes logical forms which corresponds to strings (e.g., `(string physicist)`)
and converts them into Freebase logical forms (e.g.,
`(fb:people.person.profession fb:en.physicist)`).

Type the following utterances into the prompt:

    Ulm
    physicists

Each utterance should return a list of candidate entities.

Now for compositionality, add the following rules, which will take two
logical forms and either perform an intersection or a join:

    (rule $Set ($Unary $PartialSet) (MergeFn and))
    (rule $PartialSet ($Binary $Entity) (JoinFn binary,unary unaryCanBeArg1))
    (rule $ROOT ($Set) (IdentityFn))

This allows us to type in:

    physicists born in Ulm

You should get many candidates, but the correct answer should appear in the list:

    (derivation (formula (and (fb:people.person.profession fb:en.physicist) (!fb:location.location.people_born_here fb:en.ulm))) (value (list (name fb:en.albert_einstein "Albert Einstein"))) (type fb:people.person))

For an example of a more complex grammar, look at `data/emnlp2013.grammar`.

## Exercises

1. Convert the following natural language utterances into lambda-DCS logical forms:

    `city with the largest area`

    `top 5 cities by area`

    `countries whose capitals have area at least 500 squared kilometers`

    `states bordering Oregon and Washington`

    `second tallest mountain in France`

    `country with the most number of rivers`

You should familiarize yourself with the [Freebase schema](http://www.freebase.com/schema) to see which
predicates to use.

Execute these logical forms on the `geofreebase` subset to verify your answers.

2. Write a grammar that can parse the above utterances into a set of candidates
containing the true logical form you annotated above.  Train a model (remember
to add features) so that the correct logical forms appear at the top of the
candidate list.

## Background reading

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
