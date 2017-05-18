package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.*;

/**
 * Useful utilities and dummy system components for writing tests.
 *
 * @author Roy Frostig
 */
public final class TestUtils {
  private TestUtils() { }

  public static Grammar makeAbcGrammar() {
    Grammar g = new Grammar();
    g.addStatement("(rule $X (a) (ConstantFn (string a)))");
    g.addStatement("(rule $X (b) (ConstantFn (string b)))");
    g.addStatement("(rule $X (c) (ConstantFn (string c)))");
    g.addStatement("(rule $X ($X $X) (ConcatFn ,))");
    g.addStatement("(rule $ROOT ($X) (IdentityFn))");
    return g;
  }

  public static Grammar makeArithmeticGrammar() {
    Grammar g = new Grammar();
    g.addStatement("(rule $Expr ($TOKEN) (NumberFn))");
    g.addStatement("(rule $Expr ($Expr $Partial) (JoinFn backward))");
    g.addStatement("(rule $Partial ($Operator $Expr) (JoinFn forward))");
    g.addStatement("(rule $Operator (plus) (ConstantFn (lambda y (lambda x (call + (var x) (var y))))))");
    g.addStatement("(rule $Operator (times) (ConstantFn (lambda y (lambda x (call * (var x) (var y))))))");
    g.addStatement("(rule $Operator (and) (ConstantFn (lambda y (lambda x (call + (var x) (var y))))))");
    g.addStatement("(rule $Operator (and) (ConstantFn (lambda y (lambda x (call * (var x) (var y))))))");
    g.addStatement("(rule $ROOT ($Expr) (IdentityFn))");
    return g;
  }

  public static Grammar makeArithmeticFloatingGrammar() {
    Grammar g = new Grammar();
    g.addStatement("(rule $Expr ($TOKEN) (NumberFn) (anchored 1))");
    g.addStatement("(rule $Expr ($Expr $Partial) (JoinFn backward))");
    g.addStatement("(rule $Partial ($Operator $Expr) (JoinFn forward))");
    g.addStatement("(rule $Operator (nothing) (ConstantFn (lambda y (lambda x (call + (var x) (var y))))))");
    g.addStatement("(rule $Operator (nothing) (ConstantFn (lambda y (lambda x (call * (var x) (var y))))))");
    g.addStatement("(rule $ROOT ($Expr) (IdentityFn))");
    return g;
  }

  public static Grammar makeNumberConcatGrammar() {
    Grammar g = new Grammar();
    g.addStatement("(rule $Number ($TOKEN) (NumberFn))");
    g.addStatement("(rule $Number ($Number $Number) (ConcatFn ,))");
    g.addStatement("(rule $ROOT ($Number) (IdentityFn))");
    return g;
  }

  public static Builder makeSimpleBuilder() {
    Builder builder = new Builder();
    builder.grammar = makeNumberConcatGrammar();
    builder.executor = new FormulaMatchExecutor();
    builder.buildUnspecified();
    return builder;
  }

  public static Dataset makeSimpleDataset() {
    return new Dataset();
  }

  public static Learner makeSimpleLearner(Parser parser, Params params, Dataset dataset) {
    return new Learner(parser, params, dataset);
  }

  public static Learner makeSimpleLearner(Builder builder, Dataset dataset) {
    return makeSimpleLearner(builder.parser, builder.params, dataset);
  }

  public static Learner makeSimpleLearner() {
    return makeSimpleLearner(makeSimpleBuilder(), makeSimpleDataset());
  }

  public static Example makeSimpleExample(String utterance) { return makeSimpleExample(utterance, null); }
  public static Example makeSimpleExample(String utterance, Value targetValue) {
    Builder builder = new Builder();
    builder.build();
    Example ex = new Example.Builder()
        .setId("_id")
        .setUtterance(utterance)
        .setTargetValue(targetValue)
        .createExample();
    ex.preprocess();
    return ex;
  }
}
