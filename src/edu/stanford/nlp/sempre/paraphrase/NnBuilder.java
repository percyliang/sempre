package edu.stanford.nlp.sempre.paraphrase;

import edu.stanford.nlp.sempre.*;
import fig.basic.Option;
import fig.basic.Utils;

import java.util.List;

/**
 * Basic components for transformation learning
 * @author jonathanberant
 */
public class NnBuilder {
  public static class Options {
    @Option public String packageName = "edu.stanford.nlp.sempre";
    @Option public String executor = "SparqlExecutor";
    @Option public List<String> trainGrammarPath;
    @Option public List<String> testGrammarPath;
  }

  public static Options opts = new Options();

  public Executor executor;
  public FeatureExtractor extractor;
  public Parser trainParser;
  public Parser testParser;
  public ContextModel contextMapper;

  public void build() {
    executor = null;
    extractor = null;
    trainParser = null;
    contextMapper = null;
    buildUnspecified();
  }

  public void buildUnspecified() {

    // Train grammar
    Grammar.opts.inPaths = opts.trainGrammarPath;
    Grammar trainGrammar = new Grammar();
    trainGrammar.read();
    
    //Test grammar
    Grammar.opts.inPaths = opts.testGrammarPath;
    Grammar testGrammar = new Grammar();
    testGrammar.read();

    // Executor
    if (executor == null) {
      executor = (Executor) Utils.newInstanceHard(opts.packageName + "." + opts.executor);
    }

    // Feature extractors
    if (extractor == null)
      extractor = new FeatureExtractor(executor);

    // Train Parser
    if (trainParser == null) 
      trainParser = new BeamParser(trainGrammar, extractor, executor);
    
    // Test parser
    if(testParser == null)
      testParser = new BeamParser(testGrammar, extractor, executor);
  }
}
