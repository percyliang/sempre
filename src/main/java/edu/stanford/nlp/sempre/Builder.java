package edu.stanford.nlp.sempre;

import com.google.common.base.Strings;

import edu.stanford.nlp.sempre.roboy.ErrorRetrieval;
import fig.basic.Option;
import fig.basic.Utils;

/**
 * Contains all the components (grammar, feature extractor, parser, parameters)
 * needed for semantic parsing.
 *
 * @author Percy Liang
 * @maintainer emlozin
 */
public class Builder {
  public static class Options {
    @Option public String inParamsPath;
    @Option public String executor = "JavaExecutor";
    @Option public String simple_executor = "JavaExecutor";
    @Option public String valueEvaluator = "ExactValueEvaluator";
    @Option public String parser = "BeamParser";
  }

  public static Options opts = new Options();

  public Grammar grammar;
  public Executor executor;
  //TODO:remove
  public Executor simple_executor;
  public ValueEvaluator valueEvaluator;
  public FeatureExtractor extractor;
  public Parser parser;
  public Params params;
  public ErrorRetrieval error_retrieval;

  public void build() {
    grammar = null;
    executor = null;
    simple_executor = null;
    valueEvaluator = null;
    extractor = null;
    parser = null;
    params = null;
    error_retrieval = null;
    buildUnspecified();
  }

  public void buildUnspecified() {
    // Grammar
    if (grammar == null) {
      grammar = new Grammar();
      grammar.read();
      grammar.write();
    }

    // Executor
    if (executor == null)
      executor = (Executor) Utils.newInstanceHard(SempreUtils.resolveClassName(opts.executor));

    // SimpleExecutor
    if (simple_executor == null)
      simple_executor = (Executor) Utils.newInstanceHard(SempreUtils.resolveClassName(opts.simple_executor));

    // Value evaluator
    if (valueEvaluator == null)
      valueEvaluator = (ValueEvaluator) Utils.newInstanceHard(SempreUtils.resolveClassName(opts.valueEvaluator));

    // Feature extractor
    if (extractor == null)
      extractor = new FeatureExtractor(executor, simple_executor);

    // Parser
    if (parser == null)
      parser = buildParser(new Parser.Spec(grammar, extractor, executor, simple_executor, valueEvaluator));

    // Error Retrieval
    if (error_retrieval == null)
      error_retrieval = new ErrorRetrieval();

    // Parameters
    if (params == null) {
      params = new Params();
      if (!Strings.isNullOrEmpty(opts.inParamsPath))
        params.read(opts.inParamsPath);
    }
  }

  public static Parser buildParser(Parser.Spec spec) {
    switch (opts.parser) {
      case "BeamParser":
        return new BeamParser(spec);
      case "ReinforcementParser":
        return new ReinforcementParser(spec);
      case "FloatingParser":
        return new FloatingParser(spec);
      default:
        // Try instantiating by name
        try {
          Class<?> parserClass = Class.forName(SempreUtils.resolveClassName(opts.parser));
          return (Parser) parserClass.getConstructor(spec.getClass()).newInstance(spec);
        } catch (ClassNotFoundException e1) {
          throw new RuntimeException("Illegal parser: " + opts.parser);
        } catch (Exception e) {
          e.printStackTrace();
          throw new RuntimeException("Error while instantiating parser: " + opts.parser + "\n" + e);
        }
    }
  }
}
