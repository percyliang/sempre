# SEMPRE 2.0 documentation

This document describes SEMPRE 2.0 in detail.  See 
[the tutorial](TUTORIAL.md) for a more engaging introduction.  This document
assumes a modest understanding of how SEMPRE works.

If you find any bugs, please report them or fix them (file a GitHub issue or
submit a pull request)!

## Code organization

SEMPRE is designed to be modular so that one can plug in various types of
parsers, logical forms, executors, etc.

### Modules 

The SEMPRE code is broken up into a set of modules (see `Makefile` to see the
list).  The code of the core module is in:

    src/edu/stanford/nlp/sempre

The code for each of the other non-essential modules is in:

    src/edu/stanford/nlp/sempre/<module-name>

- core: contains all the basic code (this is kept to a minimum)
- cache: allows us to run a cache server (implements a simple key-value store for things like SPARQL results)
- corenlp: contains the glue code that allows us to use Stanford CoreNLP
- freebase: needed to handle logical forms which are database queries (SPARQL, Freebase)

### Java classes

Here are the basic Java classes in `core`, organized by function:

Logical forms:

- `Formula` (`ValueFormula`, `SuperlativeFormula`, ...): logical form
- `Derivation`: contains `Formula` and the way it was constructed
- `SemanticFn` (`JoinFn`, `NumberFn`, ...): takes multiple `Derivation`s and combines them into one
- `Rule`: associated with a `SemanticFn` which specifies how to combine `Derivation`s
- `Grammar`: a set of `Rule`s

Execution:

- `Value` (`NameValue`, `ListValue`, ...): represents a denotation
- `Executor` (`JavaExecutor`, `freebase.SparqlExecutor`): takes a `Formula` and returns a value
- `ValueEvaluator` (`ExactValueEvaluator`, `freebase.FreebaseValueEvaluator`): evaluates how correct a denotation is (with respect to the correct answer)

Parsing:

- `Example`: specifies an utterance and a `ContextValue`
- `LanguageAnalyzer` (`SimpleLanguageAnalyzer`, `corenlp.CoreNLPAnalyzer`): does basic linguistic analysis on an `Example`
- `FeatureExtractor`: takes an `Example` and `Derivation` and extracts features for scoring
- `Params`: parameters of the semantic parser
- `Parser` (`BeamParser`, `FloatingParser`, `ReinforcementParser`): takes `Example`s and produces `Derivation`s, scoring them using `FeatureExtractor` and `Params`

Learning:

- `Dataset`: specifies a set of `Example`s
- `Learner`: takes a `Dataset` and produces parameters `Params`

# Logical forms

There are two types of logical forms, depending on the application:

- Lambda DCS logical forms for querying databases (declarative); use `freebase.SparqlExecutor`
- Simple Java logical forms for doing everything else (procedural); use `JavaExecutor`

The interpretation/denotation of a logical form depends on the `Executor` that
is being used (specified via the `-Builder.executor` option).  `Executor`s
(i.e., classes that extend `Executor`) define how logical forms are mapped to
denotations.  `JavaExecutor` treats the logical forms as simple Java
code and just runs it.  `SparqlExecutor` treats the logical forms as lambda DCS
expressions, which are used to query a database (e.g., via SPARQL, and e.g.,
Freebase).

Logical forms are defined recursively.  In the base case, we have primitive
formulas, which are either variables are values.  In the recursive case, we
build up larger formulas out of smaller formulas using various composition
constructs.  It is useful to think of the logical form as a tree, where the
leaves are the primitive formulas and each non-leaf subtree as corresponpding
to one composition operation.

## Primitive logical forms

### ValueFormula

A `ValueFormula` is a formula that wraps a specific denotation `Value`.
The possible `Value`s, along with an example are as follows:

- `BooleanValue`: standard boolean value.  For example:

        (boolean true)
        (boolean false)

- `NumberValue`: standard floating point number with optional units.  For example:

        (number 2)
        (number 2.5)
        (number 2.5 fb:en.meter)

- `StringValue`: standard UTF-8 string.  For example:

        (string hello)
        (string "hello")
        (string "hello world")
        (string hello\ world)

    Note that the first two and the last two are identical.
        
- `DateValue`: year-month-day representation of a date.  For example:
  
        (date 2014 12 21)   # December 21, 2014
        (date 2014 -1 -1)   # 2014
        (date -1 12 21)     # December 21
        (date -1 12 -1)     # December

    In general:

        (date <year> <month> <day>)

- `NameValue`: represents a predicate symbol (either representing an entity or relation in a knowledge base);
  the symbol has an optional description.  For example:
  
        fb:en.barack_obama
        (name fb:en.barack_obama)
        (name fb:en.barack_obama "Barack Obama")

    all refer to the same symbol.  Note: the first one is only valid as a string
    representation of a `Formula` (which represent a `Value`) &mdash; to be used
    in logical forms.  The second two are valid representations of a `Value`
    &mdash; usually returned from `Executor`s.

- `ListValue`: represents a list of values.  For example:

        (list)
        (list (number 1) (number 2) (string hello))

    In general:

        (list <value-1> ... <value-n>)

- `TableValue`: represents a table (matrix) of values, where each column has a string.  Here is an example table:
    
        (table (State Capital) ((name fb:en.california) (name fb:en.sacramento)) ((name fb:en.oregon) (name fb:en.salem)))

    In general:

        (table (<header-string-1> ... <header-string-n>) (<row-1-value-1> ... <value-1-value-n>) ...)

There are some more arcane `Value`s (see `Values.java` for a list), but they
are not that important from the point of view of specifying a logical form.

### VariableFormula

Besides `ValueFormula`s, the other primitive formula is a `VariableFormula`,
which represent variables in lambda expressions.  An example `VariableFormula` is:

    (var x)

which simply represents a variable with the name `x`.  On their own, these
formulas are rather uninteresting.  However, they are integral subcomponents of
`LambdaFormula`s which are described later.

## Compositional logical forms

Now we describe the compositional logical forms, those that allow us to
construct larger logical forms from smaller ones.

For executing simple Java programs (`JavaExecutor`), the only relevant
composition is `CallFormula`.  The rest are for building lambda DCS documents
for execution using the `freebase.SparqlExecutor`.

### CallFormula

A `CallFormula` has the following form:

    (call <function> <arg-1> <arg-2> ... <arg-n>)

The `<function>` is a string specifying any Java function and the
`<arg-i>` entries are logical forms.  The function is one of the following:

- A static method:

        (call java.lang.Math.cos (number 0))

- An instance method (`arg-1` is used as `this`):

        (call .length (string hello))                            # (number 5)
        (call .indexOf (string "what is this?") (string is))     # (number 5)

- A shortcut, which maps onto to a static method in `JavaExecutor.BasicFunctions`:

        (call < (number 3) (number 4))              # (boolean true)
        (call + (number 3) (number 4))              # (number 7)
        (call + (string 3) (string 4) (string 5))   # (string 345)

  Note that operator overloading is supported, and resolution is based on the
  argument types.  Conditionals are supported but both true and false branches
  are evaluated:

        (call if (call < (number 3) (number 4)) (string yes) (string no))   # (string yes)

  We can perform iteration via functional programming.  These list functions
  take a `ListValue` and a `LambdaFormula` as arguments:

        (call map (list (number 3) (number 4)) (lambda x (call * (var x) (var x))))                 # (list (number 9) (number 16))
        (call select (list (number 3) (number 4)) (lambda x (call < (var x) (number 3.5)))))        # (list (number 3))
        (call reduce (list (number 3) (number 4)) (lambda x (lambda y (call + (var x) (var y))))))  # (number 7)

  To create a list of indices:

        (call range (number 0) (number 3))   # (list (number 0) (number 1) (number 2))

  Note that the goal is not to support the ability to write arbitrarily complex
  programs using this fairly verbose language.  In practice, you would write
  your own custom modules, and use these mechanisms to glue a small set of modules together.

### JoinFormula

Now we embark on our tour of lambda DCS logical forms.  See the [lambda DCS
documentation](http://arxiv.org/pdf/1309.4408.pdf) for a more mathematical
description.

A brief note about terminology in lambda DCS.  Unaries and binaries are logical
forms which represent sets and (binary) relations:

- Unary (e.g., `(fb:type.object.type fb:people.person)`): denotes a set of entities
- Binary (e.g., `fb:location.location.area`): denotes a set of entity-pairs (includes functions too).
  We consider the first argument the *head* and the second argument the *modifier*.  For example:

        fb:location.location.containedby     denotes     {(fb:en.san_francisco, fb:en.california), ...}
        fb:people.person.place_of_birth      denotes     {(fb:en.barack_obama, fb:en.honolulu), ...}
        !fb:people.person.place_of_birth     denotes     {(fb:en.honolulu, fb:en.barack_obama), ...}

    The notational convention is that `!` reverses the head and modifier.

    In general:

        <binary>   denotes    {(<head-1>, <modifier-1>), ...}

A `JoinFormula` combines a binary with a unary:

    (<binary> <unary>)

This is a database join on the second argument of the binary and the unary, and
a projection onto the first argument of the binary.

Here are some examples:

    (fb:location.location.containedby fb:en.california)
    (!fb:people.person.place_of_birth fb:en.barack_obama)
    (fb:people.person.place_of_birth (fb:location.location.containedby fb:en.california))

Sometimes, the binary is special, and the resulting logical form denotes an
infinite set:

    (!= fb:en.california)     # denotes entities not equal to California
    (> (number 5))            # denotes numbers greater than 5
    (STRSTARTS (string A))    # denotes strings starting with "A"

Note: One might be tempted to think of `JoinFormula` as function application.
This is sometimes valid, but one has to be very careful about which is the head
and which is the modifier.  When the binary is a `LambdaFormula`, then function
application is generally the right mental model.

### MergeFormula

A `MergeFormula` combines two formulas and represents either their set
intersection (and) or set union (or):

    (and <formula-1> <formula-2>)
    (or <formula-1> <formula-2>)

For example, *scientists born in Seattle*:

    (and (fb:people.person.profession fb:en.scientist) (fb:people.person.place_of_birth fb:en.seattle))

Here are *people who are born in Honolulu or Seattle*:

    (or (fb:people.person.place_of_birth fb:en.honolulu) (fb:people.person.place_of_birth fb:en.seattle))

### NotFormula

A `NotFormula` takes a formula denoting a set and denotes the complement of that set:

    (not <formula>)

For example, *cities not in California*:

    (and (fb:type.object.type fb:location.citytown) (not (fb:location.location.containedby fb:en.california)))

### AggregateFormula

An `AggregateFormula` takes a formula representing a set and performs some operation.  The general form is:

    (<mode> <formula>)

where `<mode>` defines the type of aggregation to be performed while
`<formula>` denotes/defines the set that we are aggregating over.  The possible
modes (with their corresponding semantics) are listed below:

- `count`: Returns the cardinality of a set.
- `sum` : Returns the sum of the elements in the set. 
- `avg` : Returns the average/mean of the elements in the set.
- `min` : Returns the minimum element of a set.
- `max` : Returns the maximum element of a set.

Note that the latter four modes can only be applied to logical forms denoting sets of numbers.

For example, *number of people born in Honolulu*:

    (count (fb:people.person.place_of_birth fb:en.honolulu))  # (number 570)

Here is the *maximum height of any mountain*

    (max (!fb:geography.mountain.elevation (fb:type.object.type fb:geography.mountain)))   # (number 8848 fb:en.meter)

### ArithmeticFormula

An `ArithmeticFormula` combines two logical forms denoting numbers and also
denotes a number.

The form of an `ArithmeticFormula` is as follows:

    (<operator> <arg-1> <arg-2>)

The `<operator>` entry is either `+`, `-`, `*`, or `/`, and the two arguments
are formulas that denote numeric values (including dates, although the support
there is a bit sketchy).  Here are some examples:

    (+ (number 5) (number 3))   # (number 8)
    (* (number -1) (number 3))  # (number -3)

Here's how to compute the difference in height between two people:

    (- (!fb:people.person.height_meters fb:en.michael_jordan) (!fb:people.person.height_meters fb:en.barack_obama))   # (number 0.130 fb:en.meter)

Note that we could not define these arithmetic formulas via `JoinFormula`
because they are ternary relations rather than binary ones.

### ReverseFormula

A `ReversalFormula` takes a binary logical form denoting a set of head-modifier
pairs and denotes the corresponding set of modifier-head pairs.

Recall:

    fb:people.person.place_of_birth              denotes     {(fb:en.barack_obama, fb:en.honolulu), ...}

The following is equivalent to `!fb:people.person.place_of_birth`:

    (reverse fb:people.person.place_of_birth)    denotes     {(fb:en.honolulu, fb:en.barack_obama), ...}

But reversal can be applied to compositional binaries built using `LambdaFormula`:

    (reverse (lambda x (fb:people.person.places_lived (fb:people.place_lived.location (var x)))))

You can check that the above is equivalent to:

    (lambda x (!fb:people.place_lived.location (!fb:people.person.places_lived (var x))))

In both logical forms, the location is in the head position and person is in
the modifier position.

### LambdaFormula

Now the serious stuff begins.  Up until now, all the compositional logical
forms were unaries (which denote sets).  Lambda abstraction allows us to
construct logical forms that denote binary, ternary, and
other higher-order relations.

The general form includes a variable and body formula which uses the variable:

    (lambda <var> <body>)

If the body is a unary, then the resulting logical form is a binary, where the
unary represents the head and the variable represents the modifier.  Let us see some examples.

The first one is equivalent to `fb:people.person.place_of_birth`:

    (lambda x (fb:people.person.place_of_birth (var x)))

This logical form denotes a binary relation where the head is the person and
the modifier is the location:

    (lambda x (fb:people.person.places_lived (fb:people.place_lived.location (var x))))

The more complex (but very useful) example represents a binary where the head
is a person and the modifier is the number of children the person has:

    (reverse (lambda x (count (!fb:people.person.children (var x)))))   denotes   {(fb:en.barack_obama, 2), ...}

#### Macro substitution

Another more syntactic view of `LambdaFormula`s is that they are just logical
forms with holes.  In this case, a `JoinFormula` where the binary is a
`LambdaFormula` performs macro substitution.  Here is an example of a join
of a `LambdaFormula` binary and an unary:

    ((lambda x (fb:people.person.places_lived (fb:people.place_lived.location (var x)))) fb:en.seattle)

In the macro substitution view, the variable `x` becomes bound to `fb:en.seattle`, and the resulting logical form is:

    (fb:people.person.places_lived (fb:people.place_lived.location fb:en.seattle))

which is equivalent.  This process is called beta-reduction.  But one must
exercise care, since the macro substitution view and the original higher-order
relation view are different.  Consider:

    ((lambda x (and (!fb:people.person.place_of_birth (var x)) (!fb:people.deceased_person.place_of_death (var x)))) (fb:type.object.type fb:people.person))

In the higher-order relation view, the binary relates locations (head) to
people (modifier).  The resulting logical form denotes the set of locations
which are both the place of birth and place of death of some person.  However,
if we do macro substitution, then we get the set of locations which are either
the place of birth of someone or the place of death of someone (possibly
different).

Macro substitution is triggered by explicit beta reduction (see `JoinFn` with
`betaReduce` below), and this is typically during the intermediate steps of
constructing logical forms.

We can construct logical forms that have more than one argument:

    (lambda binary (lambda unary ((var binary) (var unary))))

We can apply this ternary logical form to two arguments,

    (((lambda binary (lambda unary ((var binary) (var unary)))) fb:people.person.place_of_birth) fb:en.seattle)

which after beta reduction results in the very mundane

    (fb:people.person.place_of_birth fb:en.seattle)

In lambda DCS, all logical forms are either unaries or binaries.  Ternaries and
beyond are exclusively used for macro substitution to construct logical forms
in a controlled compositional way.

### SuperlativeFormula

A `SuperlativeFormula` formula takes a unary denoting a set and a binary
denoting a relation between elements of that set (head) and a number
(modifier), and denotes a set representing extreme elements based on the
relation.

Here is the general form:

    (<mode> <rank> <count> <unary> <binary>) 

The different pieces are as follows:

- `<mode>` is either `argmin` or `argmax`
- `<rank>` is an integer indicating the offset in the sorted list of the entities
- `<count>` specifies the number of elements to return
- `<unary>` is the base set that we're drawing from
- `<binary>` specifies the relation by which we sort the elements

Here is *the city with the largest area*:

    (argmax 1 1 (fb:type.object.type fb:location.citytown) fb:location.location.area)

The *second largest city by area*:

    (argmax 2 1 (fb:type.object.type fb:location.citytown) fb:location.location.area)

The *five largest cities by area*:

    (argmax 1 5 (fb:type.object.type fb:location.citytown) fb:location.location.area)

The *person who has the most number of children* (who, in Freebase, turns out
to be Chulalongkorn with a whopping 62):

    (argmax 1 1 (fb:type.object.type fb:people.person) (reverse (lambda x (count (!fb:people.person.children (var x))))))

### MarkFormula

In a way, `MarkFormula` essentially allows us to do anaphora.  If we think of
simple lambda DCS (joins and merges) as producing tree-structured logical
forms, `MarkFormula` allows us to have non-tree edges between the root of a
subtree and a node in that subtree.

Recall `LambdaFormula` creates a binary from a unary.  A `MarkFormula` creates
a unary from a unary, which simply binds the body unary to the variable (which
appears in the body).

The general form:

    (mark <var> <body>)

Here is *those whose place of birth is the same as their place of death*:

    (mark x (fb:people.person.place_of_birth (!fb:people.deceased_person.place_of_death (var x))))

Note that `x` binds to the head of `fb:people.person.place_of_birth`,
representing the person in question.  This allows us to use this head again
deeper in the logical form.  Here's a pictorial representation:

    x -- [fb:people.person.place_of_birth] --> ?
    ? -- [!fb:people.deceased_person.place_of_death] --> x

where `?` denotes an unnamed intermediate variable.

# Semantic functions

From the tutorial, recall that a grammar is a set of rules of the following
form: 

    (rule <target-category> (<source-1> ... <source-k>) <semantic-function>)

Basically, these rules specify how derivations belonging to the source categories are combined
into derivations that correspond to the target category.
Semantic functions are the workhorses of these rules:
they take in a set of source derivations and apply transformations (defined via Java code) on these derivations
in order to produce a derivation that is a member of the target category.

Since semantic functions are built from arbitrary Java code (any class extending `SemanticFn`), there is a great deal of flexibility,
and knowing how to properly use semantic functions is perhaps the most important step in working with SEMPRE.
This section defines the semantic functions that come pre-packaged with SEMPRE.
In all likelihood, these semantic functions will provide all the functionality that you need.

Some of these semantic functions will rely on other packages/classes/options being used/set in order to function properly.
For example, many semantic functions require that the Stanford CoreNLP package is loaded (via the `-languageAnalyzer corenlp.CoreNLPAnalyzer` option);
this package contains many general purpose NLP algorithms that extract linguistic information (e.g., parts of speech) from utterances.
Dependences on packages such as CoreNLP will be noted when necessary.

## Special categories:

Some special categories that you should now in order to effectively write grammars and use semantic functions:

- `$TOKEN`: selects a single atomic word from the utterance.
- `$PHRASE`: selects any subsequence of tokens/words from the utterance.
- `$LEMMA_TOKEN`: selects any atomic word from the utterance and lemmatizes the word. For example, "arriving" would become "arrive".
- `$LEMMA_PHRASE`: selects any subsequence of tokens/words and lemmatizes them.

Both of the special categories that rely on lemmatization will only function
properly if the `corenlp.CoreNLPAnalyzer` is loaded.  Otherwise, the
lemmatization will simply amount to making everything lowercase.

There are two broad informal classes of semantic functions: primitive semantic
functions and compositional semantic functions.  The primitive ones take
phrases in the natural language utterance, filters them, and produces some
derivation generally with a simple logical form.  The compositional ones take
these simple derivations and combines them into larger derivations,
usually generating larger logical forms.

## Primitive semantic functions

Primitives are basic semantic functions that directly map spans (i.e., subsequences) of an utterance to logical forms. 
Most grammars will rely on primitives as the "bottom" rules, i.e., the rules that have some span of the utterance as their right hand side (RHS)
and which will other rules build off of. 

### ConstantFn

This is a basic primitive function which allows for you to hard-code basic rules, such as 

    (rule $ROOT (barack obama) (ConstantFn fb:en.barack_obama fb:people.person))

which would parse the following utterance into its corresponding entity (i.e., logical form):

    barack obama        # (name fb:en.barack_obama)

Note the form that `ConstantFn` takes, i.e. `(ConstantFn formula type)`, and that the `type` argument is optional, meaning that

    (rule $ROOT (barack obama) (ConstantFn fb:en.barack_obama))

would also parse the above utterance. 
However, note that if types are not explicitly added, the system relies on automatic type inference (see the section on Types below).

Another example of `ConstantFn` would be the following: 

    (rule $ROOT (born in) (ConstantFn fb:people.person.place_of_birth (-> fb:location.location fb:people.person))))

which would parse the phrase `born in` to a relation/binary logical form (as opposed to an entity) as follows:

    born in         # (name fb:people.person.place_of_birth)

Lastly, note that in both these cases the parsed logical form is prefixed with `name`.
This denotes that the logical form has a `NameValue` denotation, which simply means that it represents a logical predicate/entity
and not some other primitive (e.g., a `NumberValue` or `DateValue`).

### SimpleLexiconFn (reliant on having a SimpleLexicon)

This function allows you to map phrases/tokens to logical entities in a more scalable manner than hard-coding everything with `ConstantFn` directly in your grammar.
Suppose you have a `SimpleLexicon` loaded from a JSON file containing the entries:

    {'lexeme' : 'barack obama', 'formula' : 'fb:en.barack_obama', 'type' : 'fb:people.person'} 
    {'lexeme' : 'born in', 'formula' : 'fb:people.person.place_of_birth', 'type' : '(-> fb:location.location fb:people.person)'} 

then the rule

    (rule $ROOT ($PHRASE) (SimpleLexiconFn (type fb:people.person)))

will parse the following:

    barack obama       # (name fb:en.barack_obama)

and the rule

    (rule $ROOT ($PHRASE) (SimpleLexiconFn (type (-> fb:location.location fb:people.person))))

will parse 

    born in        # (name fb:people.person.place_of_birth) 

Thus, the function of `SimpleLexiconFn` is similar to `ConstantFn`, but it facilitates more modularity
since the lexical items are contained within the `SimpleLexicon` (loaded from a JSON) instead of being hard-coded into the grammar.

In the above examples, this type annotation is not particularly important, but consider the case where we have the following lexical entries:

    {"lexeme" : "Lincoln", "formula" : "fb:en.abraham_lincoln", "type" : "fb:people.person"} 
    {"lexeme" : "Lincoln", "formula" : "fb:en.lincoln_nevada", "type" : "fb:location.location"} 

Both of these lexical entries have the same trigger phrase (i.e., lexeme).
However, they correspond to very distinct entities (one is a former president, while the other is a city in Nevada).
By leveraging the type annotation, we can specify which entity we actually want to trigger.
For example, the rule
 
    (rule $ROOT ($PHRASE) (SimpleLexiconFn (type fb:location.location)))

would ensure that we only trigger the entity that corresponds to the city.
Alternatively, if we wanted both entities to be triggered (e.g., if type-checking later on will handle the ambiguity),
then we could write:

    (rule $ROOT ($PHRASE) (SimpleLexiconFn (type fb:type.any)))

### NumberFn (partially reliant on the CoreNLPAnalyzer)

`NumberFn` is the basic primitive function for parsing numbers from an utterance.
For example,

    (rule $ROOT (NumberFn))

would parse the following strings into the following logical forms:


    2.3               # (number 2)
    four              # (number 4)
    3 million         # (number 3000000)
    
The last one works only if `-languageAnalyzer corenlp.CoreNLPAnalyzer` is set.

### DateFn (reliant on the CoreNLPAnalyzer) 

`DateFn` is the basic primitive function for parsing date values.
For example,

    (rule $ROOT (DateFn))

would parse the following strings into the following logical forms:

    Thursday, December 4th        # (date -1 12 4)
    The 12th of Nov, 2012         # (date 2012 11 12)
    August                        # (date -1 8 -1) 

Note that the logical representation of dates here (defined in the `DateValue` class) is distinct from Java's date (or JodaTime etc.)
and that only dates not times are represented. 
The logical form representation of a `DateValue` is simply `(date year month day)`, with one-based indexing for the months. 
The above examples also illustrate how missing aspects of dates are treated: 
if any part of a date (day, month, or year) is left unspecified, then the `DateValue` logical representation inserts -1s in those positions.

## Filtering semantic functions

You might find that your grammar is generating far too many candidate
derivations, resulting in the correct derivations falling off the beam.  After
all, the number of derivations does tend to grow exponentially with the
sentence length.

The semantic functions described below help to ameliorate this issue by
allowing you to filter down the phrases that are considered.  In other words,
they help control how many logical forms you generate by allowing you to
specify more precise situations in which rules should fire.

This is particularly important when your lexicon (like our EMNLP 2013 system)
contains a lot of noisy entries.

### FilterSpanLengthFn

This semantic function allows you to filter out the length of spans that you trigger on. For example,

    (rule $Length2Span ($PHRASE) (FilterSpanLengthFn 2)) 

produces a new category `$Length2Span` which contains only phrases of length 2, 
which is useful for limiting the number of phrases you compute on.
For instance, we could combine this with the simple lexicon function we used above and say

    (rule $ROOT ($Length2Span) (SimpleLexiconFn (type fb:type.any)))

and this would parse

    barack obama       # (name fb:en.barack_obama)
 
but would make running the grammar faster than if we replaced `$Length2Span` with `$PHRASE`, since `$PHRASE` will try all possible lengths, 
and we know that our lexicon only contains length 2 phrases.

### FilterPosTagFn (relies on CoreNLPAnalyzer)

`FilterPosTagFn` allows you to extract spans or tokens that are composed only of certain parts of speech (POS) tags.
For example,

    (rule $ProperNoun ($TOKEN) (FilterSpanPosTag token NNP NNPS)) 

produces a new category `$ProperNoun` which contains words that are proper nouns.
This would accept phrases like:

      honolulu
      obama

and assign them to the `$ProperNoun` category.

Note the options that are passed: `token` specifies that we want to look at single tokens and `NNP NNPS` are the POS tags for proper nouns.
If you wanted to get multi-word proper nouns then you would use
 
    (rule $ProperNounPhrase ($PHRASE) (FilterSpanPosTag span NNP NNPS))

Note that we both changed the RHS category to `$PHRASE` and changed the `token` option to `span`.
This would accept things like

    honolulu hawaii
    barack obama

but note that the entire span must have the same POS tag (that is in one of the accepted categories).
Also, it will only accept the maximal span, meaning that `barack obama` will NOT be parsed three times as `barack`, `obama`, and `barack obama`.

Note also that, unlike the previous examples for simpler semantic functions, the logical form is not written out next to the phrases.
This is because the above rule to does not rewrite to `$ROOT` (i.e., it does not have `$ROOT` as a LHS),
and thus, this rule would accept the example phrases above, but it does not parse them directly to a logical form.
Other rules which take `$ProperNounPhrase` as a RHS are necessary to complete a grammar with this rule. 
Most of the filtering functions discussed below will also have this flavor.

### FilterNerSpanFn (relies on CoreNLPAnalyzer)

This `SemanticFn` allows you to extract spans that are named entities (NEs). 
For example,

    (rule $Person ($Phrase) (FilterNerSpan PERSON))

produces a new category (`$Person`) which contains phrases that the CoreNLPAnalyzer labels as referring to people.
This would accept phrases like

    barack obama
    michelle obama

Note that unlike our earlier examples where we parsed these phrases corresponding to an entity/person, 
the LHS side of our rule in this case is a new category `$Person`.
The idea is that you would use this new category to restrict what you apply rules on further up in the derivation.
For example, you may want to change our earlier `SimpleLexiconFn` example rule to

    (rule $ROOT ($Person) (SimpleLexiconFn (type fb:people.person)))

since you know that this rule should be restricted to spans that may contain a mention of a person.

### ConcatFn

This function allows you to concatenate strings, giving you more fine-grained control over how categories are constructed over spans,
which is useful for doing things that are not possible with the other filter functions. 
Concretely, say you wanted to look for spans of the utterance that may correspond to binaries,
you would probably want to look at verbs (e.g., "lived", "born").
However, triggering only on verbs is too restrictive; for example, some binaries take their meaning from verb-preposition pairs.
In fact, the example of "born in" used previously is exactly such a phrase.
In order to handle this case, we could use `ConcatFn` with the following rules:

    (rule $Verb ($TOKEN) (FilterSpanPosTag token VB VBD VBN VBG VBP VBZ))
    (rule $Prep ($TOKEN) (FilterSpanPosTag token IN)) 
    (rule $VerbPrep ($Verb $Prep) (ConcatFn " "))

These rules would create a new category (`$VerbPrep`) that contains two word phrases consisting of a verb followed by a preposition. 
One way we could leverage this category would be with a rule that uses `SimpleLexiconFn`, such as the following:
 
    (rule $Relation ($VerbPrep) (SimpleLexiconFn (type (-> fb:type.any fb:type.any))))

This rule basically says that when we look for binaries, we shouldn't look at any phrase, we should restrict to phrases consisting
of verbs followed by prepositions. 
Of course, there are other grammatical constructions that could give rise to a binary, and you would need to add new rules/categories for these.
Nevertheless, using filtering to narrow down to the spans which trigger certain rules is a very powerful tool.

## Compositional semantic functions

Compositional semantic functions are used to create larger derivations from
smaller ones.

It is important to distinguish these semantic functions, which also work
compositionally, from the definition of the recursive definition of logical
forms in the previous section.  One semantic function could easily construct a
logical form that involved multiple logical compositions, for example, going
from *city* (`fb:location.citytown`) to *largest city* (`(argmax 1 1
(fb:type.object.type fb:location.citytown) fb:location.location.area)`).

### IdentityFn 

`IdentityFn` takes a logical form and returns the same logical form.

This is the most basic composition function, useful for refactoring grammar categories. 
For example, suppose you have the following rule that parses things into entities.

    (rule $Entity ...)

You can then write:

    (rule $ROOT ($Entity) (IdentityFn))

### JoinFn

`JoinFn` can be used in a number of different ways.

#### Joining binaries and unaries

The first way in which `JoinFn` combines a binary and a unary is as follows.
Recall that each binary (e.g., `fb:people.person.place_of_birth`) has a head
(also called arg0 or return) and modifier (also called arg1 and argument).

Just for clarity, the concrete edge in the knowledge graph is:

    head -- [fb:people.person.place_of_birth] --> modifier

and the type of `fb:people.person.place_of_birth` is

    (-> modifier_type head_type)

The way to think about a binary is not a function that returns the places of
births of people, but rather as a relation connecting people (in the head
position) with locations (in the modifier position).

When applying `JoinFn`, the unary can be joined with either `arg0` or `arg1`,
and either the binary can come before the unary in the sentence or vice-versa.
Thus, there are four ways in which a binary may be joined with a unary.
These four possibilities are defined via the following two options:

    - Ordering: either `binary,unary` or `unary,binary`
    - Position: `unaryCanBeArg0` or `unaryCanBeArg1` or both

To illustrate the two most common settings for these options,
suppose we have the following setup:

    (rule $Entity (barack obama) (ConstantFn fb:en.barack_obama))
    (rule $Entity (honolulu) (ConstantFn fb:en.honolulu))
    (rule $Binary (birthplace) (ConstantFn fb:people.person.place_of_birth))

then we can write

    (rule $ROOT ($Binary $Entity) (JoinFn binary,unary unaryCanBeArg1))

These rules will parse the following utterance as follows:

    birthplace honolulu       # (fb:people.person.place_of_birth fb:en.honolulu)

This corresponds to standard forward application of a binary, and indeed we can
equivalently write:

    (rule $ROOT ($Relation $Entity) (JoinFn forward))

Alternatively, we can alter the arguments and write

    (rule $ROOT ($Entity $Relation) (JoinFn unary,binary unaryCanBeArg1))

or equivalently

    (rule $ROOT ($Entity $Relation) (JoinFn backward))

and this will parse the following into the same logical form:

    honolulu birthplace       # (fb:people.person.place_of_birth fb:en.honolulu)

If we use 

    (rule $ROOT ($Relation $Entity) (JoinFn binary,unary unaryCanBeArg0))

we can parse:

    birthplace barack obama   # (!fb:people.person.place_of_birth fb:en.barack_obama)

#### Macro substitution

The second way to use `JoinFn` is as macro substitution.  Simply add the
`betaReduce` flag.  For example:

    (rule $LambdaRelation (birthplace) (ConstantFn (lambda x (!fb:people.person.place_of_birth (var x))))) 
    (rule $ROOT ($LambdaRelation $Entity) (JoinFn forward betaReduce))

This will parse:

    birthplace barack obama     # (!fb:people.person.place_of_birth fb:en.barack_obama)

#### Multi-argument macro substitution

So far, `JoinFn` takes two arguments, but we can also specify one of the arguments:

    (rule $ROOT (birthplace $Entity) (JoinFn forward betaReduce (arg0 (lambda x (!fb:people.person.place_of_birth (var x))))))

This rule also parses *birthplace barack obama* in the expected way.  An
equivalent and much clearer way to write this is the following, where we omit
`JoinFn` completely:

    (rule $ROOT (birthplace $Entity) (lambda x (!fb:people.person.place_of_birth (var x))))

Here is another example that takes multiple arguments and forms the set
containing two entities:

    (rule $ROOT ($Entity and $Entity) (lambda x (lambda y (or (var x) (var y)))))

Going forward, you are encouraged to use this last form since it is most
transparent.  In the future, it might even be backed by another `SemanticFn`
since `JoinFn` is getting a bit out of hand.

### MergeFn

`MergeFn` simply constructs a `MergeFormula`.  For example, consider the
following rules:

    (rule $Set (female) (ConstantFn (fb:people.person.gender fb:en.female)))
    (rule $Set (scientist) (ConstantFn (fb:people.person.profession fb:en.scientist)))
    (rule $Set ($Set $Set) (MergeFn and))

We could then parse:

    female scientist    # (and (fb:people.person.gender fb:en.female) (fb:people.person.profession fb:en.scientist))

We can also use the following rule

    (rule $Set ($Set or $Set) (MergeFn or))

to do set union:

    female or scientist    # (or (fb:people.person.gender fb:en.female) (fb:people.person.profession fb:en.scientist))

### SelectFn

`SelectFn` acts as a utility composition function that aids in refactoring grammars.
More specifically, it can be used to skip over certain categories or parts of an utterance (e.g., stop words) in a controlled manner.
For example, in a question-answering setup, you may have many utterances that
start with the word "what" (or "who" etc.), but suppose this word does not
really convey any semantic content (this is not quite true).
To handle this,

    (rule $Wh (what) (ConstantFn null))
    (rule $Wh (who) (ConstantFn null))
    (rule $ROOT ($Wh $Set) (SelectFn 1)) 

This rules allow us to simply ignore the presence of *what* or *who*.

### Freebase-specific semantic functions

There are two semantic functions, `freebase.LexiconFn` and `freebase.BridgeFn`
which are used in our first sematic parsing applications, but they probably
should be avoided unless you're specifically doing Freebase QA.  Even in that
case, the main thing you should think about is:

    (rule $Entity ($PHRASE) (LexiconFn fbsearch))

which uses the Freebase Search API to look up entities.  Be aware here that the
API will generously return many candidate entities for any string you give it,
so if you are getting too many results, you should use filtering to constrain
the number of spans you look at.

### Context-specific semantic functions

`FuzzyMatchFn` is used for matching words with a context-specific graph.
More documentation coming soon.

#### ContextFn

`ContextFn` generates logical forms from the context.
Concretely, an `Example` optionally has an associated `ContextValue`, which contains an ordered list of `n` `Exchange`s.
Each `Exchange` contains an utterance, a logical form (i.e., a `Formula`), and its denotation (i.e., a `Value`).

A `ContextValue` thus represents information that is known prior to parsing the utterance in an `Example`.
For example, when running SEMPRE in interactive mode, the `ContextValue`
contains information about the last (`n`) utterance(s) that was/were parsed.
`ContextValue`s can also be provided in a dataset.

`ContextFn` can be used to resolve anaphora.
For example, suppose you are running SEMPRE in interactive mode:

    User: where has barack obama lived
    System: ((fb:people.person.places_lived fb:en.barack_obama)
The `ContextValue` of the interactive system now contains an `Exchange` corresponding to this utterance, logical form, and the computed denotation,
i.e. a list of cities including `fb:en.honolulu` and `fb:en.washington_dc`.
(Note that by default only one `Exchange`, specifically the most recent one, is stored in the system's `ContextValue`).

Now, suppose that the you then say: `where was he born`. 
Parsing this utterance requires resolving the anaphoric reference `he`, and `ContextFn` facilitates this.
Specifically, we would make the following rule:

    (rule $ContextEntity (he) (ContextFn (depth 0) (type fb:people.person)))

This rule will trigger on the word `he` and retrieve the unary `fb:en.barack_obama` from the logical
formula in the `ContextValue` (since it has type `fb:people.person`).
The `depth` argument specifies how deep/tall of a logical form subtree you want to retrieve.
In the above example, a depth of 0 was specified since we wanted to retrieve a
single bare entity (i.e., a leaf).

Alternatively, you could have said: `and which of those cities is the biggest?`.
Here, we would want to essentially "pull out" the entire logical form from the context.
Or more specifically, we want to pull out the join of the `fb:people.person.places_lived` binary and the `fb:en.barack_obama` entity.
To do this, we could make the following rule:

    (rule $ContextSetFromJoin (those cities) (ContextFn (depth 1) (type fb:places_lived)))

This rule would trigger on the phrase `those cities` and retrieve the logical form `(fb:people.person.places_lived fb:en.barack_obama)`.
Note that compared to the previous example, we changed both the type and the depth arguments.
Here, a depth of 1 indicated that we want a subtree of depth 1 (i.e., a tree with one level before the leaves), 
which corresponds, for example, to a logical form resulting from a single join.

# Grammar

So far, we have talked about grammars as being a set of rules.  But SEMPRE
`Grammar`s have additional supporting functionality which make it easier to
use.  A grammar is specified in a `.grammar` file and is loaded by the
command-line flag `-Grammar.inPaths`.

## Binarization

First, the `Grammar` performs automatic binarization of the grammar, so if you have a rule:

    (rule $ROOT ($Subject $Verb $Object) ...)

This rule gets converted to a binarized grammar where each right-hand-side has at most two elements:

    (rule $ROOT ($Subject $Intermediate) ...)
    (rule $Intermediate (Verb $Object) ...)

The consequence is that the grammar you write will not be exactly the same as
the grammar that the parser receives.

## Macros

If you find yourself writing a lot of repeated elements:

    (rule (person) (ConstantFn (fb:type.object.type fb:people.person)))
    (rule (place) (ConstantFn (fb:type.object.type fb:location.location)))
    (rule (location) (ConstantFn (fb:type.object.type fb:location.location)))

then you might want to use macros (which are all prefixed with `@`):

    (def @type fb:type.object.type)
    (def @person (@type fb:people.person))
    (def @place (@type fb:location.location))

so that you can simply write the three rules as follows:

    (rule (person) (ConstantFn @person))
    (rule (place) (ConstantFn @location))
    (rule (location) (ConstantFn @location))

The general form for defining a macro:
    
    (def @<variable> <value>)

Note that macros cannot take arguments.

## For loops

If you are defining multiple rules, with small variations, then you can use for
loops to create many rules.  For example:

    (for @x (place location)
      (rule (@x) (ConstantFn (fb:type.object.type fb:location.location)))
    )

which is equivalent to:

    (rule (place) (ConstantFn (fb:type.object.type fb:location.location)))
    (rule (location) (ConstantFn (fb:type.object.type fb:location.location)))

The general form is:

    (for @<variable> (<value-1> ... <value-n>)
      <statement-1>
      ...
      <statement-m>
    )

## Conditionals

Grammar files do not have full conditionals (the point is not to develop a
full-blown programming language here).  But if you want a grammar which is
parametrized by complexity, then you can use conditionals to include or exclude
statements:

    (when compositional
      (rule $Set ($Set $Set) (MergeFn and))
    )

    (when (not compositional)
      (rule $Set ($Entity $Entity) (MergeFn and))
    )

To enable the first rule (and disable the second), you would pass
`-Grammar.tags compositional` on the command-line.

In general:

    (when <condition>
      <statement-1>
      ...
      <statement-m>
    )

where <condition> is one of the following

    <tag>
    (not <condition>)
    (and <condition> <condition>)

## Includes

You can use the statement:

    (include <grammar-file>)

to break up your grammar file into multiple files.  It is not advisable to use
many small files; rather use the `when` construction.

# Types

SEMPRE uses a type system to associate each logical form with a type, which is
used to rule out obviously bad logical forms (e.g., `(+ fb:en.barack_obama 3)`
or `(fb:location.location.containedby fb:en.barack_obama)`).

Types are in a sense redundant with the grammar categories (we could very well
use `$Int` to capture only logical forms that compute integers.  But there are
a few differences:

- Categories could be based on how the natural language, whereas types are a
  pure function of the logical forms (e.g., `$Preposition` and `$Verb` might
  both generate logical forms that map to the type `(-> fb:type.any
  fb:type.any)`.

- The parser can handle categories efficiently (e.g., for coarse-to-fine
  parsing); it is not necessary to compute the logical form.  Types only exist
  with logical forms.

Each type is an instance of `SemType`.

## Atomic types (`AtomicSemType`)

All the **atomic types** are related via a partial ordering (the subtype
relation), which is easiest to think of as a tree structure:

    | fb:type.any
    | | fb:type.boolean
    | | fb:type.number
    | | | fb:type.int
    | | | fb:type.float
    | | fb:type.datetime
    | | fb:type.text
    | | fb:common.topic
    | | | fb:people.person
    | | | fb:location.location
    | | | ...

Atomic types can be combined to create higher-order types:

## Pair types (`FuncSemType`)

A **pair type** looks like this:

    (-> <type> <type>)

and is generally used to represent a binary relation, such as
`fb:people.person.place_of_birth`:

    (-> fb:location.location fb:people.person)     

This type might seem a bit backwards, but it is important to remember that we
should think of binaries not as functions but as relations.

In fact, the pair type looks like a function, but really behaves like a pair
type (this is more appropriate for database queries and makes it easier to
reason about).

## Union types (`UnionSemType`)

A **union type** represent a disjunction over many types:

    (union <type> ... <type>)

Note that `(union fb:type.int fb:type.float)` is a supertype of `fb:type.int`.

## Special types

- Top type (top): corresponds to the most general type (including all atomic
  and function types).

- Bottom type (bot): corresponds to type failure.

## Other notes

Some of these types are inherited from Freebase (e.g., `fb:type.float`), but
others are SEMPRE-specific and made in the spirit of the Freebase names.
Identifiers begin with `fb:` even if you are working with an application which
has little to do with Freebase, just for notational compatibility.

Types are assigned to logical forms via type inference (class `TypeInference`
in SEMPRE), and are importantly not part of the logical form itself.  This
gives us the flexibility of experimenting with different type systems without
actually changing the meaning.  Type inference in general is computationally
expensive, so the default implementation of `TypeInference` sometimes just
punt.

The main operation one can perform with two types is to compute their *meet*,
which is the most general type which is the superset of both types.  For example:

    (-> fb:location.location fb:type.any)   meet   (-> top fb:people.person)

is

    (-> fb:location.location fb:people.person)

# Running SEMPRE 

The main entry point to SEMPRE is `run`.  Run is based on the execrunner Ruby
library from fig, which provides a small domain-specific language for
generating command-line options.  (See `fig/lib/execrunner.rb` for more
documentation).

    ./run @mode=<mode> ...

The mode specifies the specific way you want to use SEMPRE.

## Execution directories

When you run something like `./run @mode=freebase ...`, an execution directory
will be created in

    state/execs/<id>.exec,

where <id> is a unique identifier.  This directory contains the stdout in the
`log` file, as well as the grammar and the parameters.

## Web interface

If you want to launch a demo, then you probably want to use the web interface,
which can be triggered by using the `-server` option.  For example:

    ./run @mode=simple -interactive false -server true
