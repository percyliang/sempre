package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.*;

/**
 * Useful utilities and dummy system components for writing tests.
 *
 * TODO: it's hard to come up with generally good defaults.
 *
 * @author Roy Frostig
 */
public class TestUtils {
  public static Grammar makeArithmeticGrammar() {
    Grammar g = new Grammar();
    g.addStatement("(rule $Number ($TOKEN) (NumberFn))");
    g.addStatement("(rule $Number ($Number $Number) (ConcatFn ,))");
    g.addStatement("(rule $ROOT ($Number) (IdentityFn))");
    return g;
  }

  public static Builder makeSimpleBuilder() {
    Builder builder = new Builder();
    builder.grammar = makeArithmeticGrammar();
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

  public static Example makeSimpleExample(String utterance) {
    Example ex = new Example.Builder()
        .setId("_id")
        .setUtterance(utterance)
        .createExample();
    ex.preprocess();
    return ex;
  }
}
