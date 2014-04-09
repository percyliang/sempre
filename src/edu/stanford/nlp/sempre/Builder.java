package edu.stanford.nlp.sempre;

import com.google.common.base.Strings;
import fig.basic.Option;
import fig.basic.Utils;

/**
 * Contains all the components (grammar, feature extractor, parser, parameters)
 * needed for semantic parsing.
 *
 * @author Percy Liang
 */
public class Builder {
  public static class Options {
    @Option public String packageName = "edu.stanford.nlp.sempre";
    @Option public String inParamsPath;
    @Option public String executor = "SparqlExecutor";
    @Option public String parser = "BeamParser";
  }

  public static Options opts = new Options();

  public Grammar grammar;
  public Executor executor;
  public FeatureExtractor extractor;
  public Parser parser;
  public Params params;

  public void build() {
    grammar = null;
    executor = null;
    extractor = null;
    parser = null;
    params = null;
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
      executor = (Executor) Utils.newInstanceHard(opts.packageName + "." + opts.executor);

    // Feature extractors
    if (extractor == null)
      extractor = new FeatureExtractor(executor);

    // Parser
    if (parser == null) {
      if(opts.parser.equals("BeamParser"))
        parser = new BeamParser(grammar, extractor, executor);
      else
        throw new RuntimeException("Illegal parser: " + opts.parser);
    }

    // Parameters
    if (params == null) {
      params = new Params();
      if (!Strings.isNullOrEmpty(opts.inParamsPath))
        params.read(opts.inParamsPath);
    }
  }
}
