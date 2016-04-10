package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;
import fig.basic.Option;

/**
 * A parser that increases complexity (uses more complex grammar / higher formula size)
 * after each training iteration.
 *
 * @author ppasupat
 */
public class IncrementalParser extends Parser implements MutatingParser {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbosity = 0;
    @Option(gloss = "Grammar to use in each iteration")
    public List<String> incGrammars = null;
  }
  public static Options opts = new Options();

  Parser baseParser;
  Spec finalSpec;
  private int currentIter = -1;
  private Grammar.Options originalGrammarOptions;

  /**
   * The given |spec| is for the last iteration (the test iteration).
   */
  public IncrementalParser(Spec spec) {
    super(spec);
    finalSpec = spec;
    originalGrammarOptions = Grammar.opts;
    setBaseParser(spec);
  }

  @Override
  public void mutate(int iter, int numIters, String group) {
    // Change baseParser according to the new iteration.
    LogInfo.begin_track("Switching to a new parser ...");
    LogInfo.logs("iter = %d/%d | group = %s", iter, numIters, group);
    if (iter != currentIter) {
      currentIter = iter;
      if (opts.incGrammars != null && opts.incGrammars.size() > iter && iter < numIters) {
        String grammarString = opts.incGrammars.get(iter);
        List<String> grammarStringParts = Arrays.asList(grammarString.split(","));
        // Warning: modifying Grammar.opts
        Grammar.opts = new Grammar.Options();
        Grammar.opts.inPaths = Collections.singletonList(grammarStringParts.get(0));
        Grammar.opts.tags = grammarStringParts.subList(1, grammarStringParts.size());
      } else {
        Grammar.opts = originalGrammarOptions;
      }
      LogInfo.logs("inPaths = %s", Grammar.opts.inPaths);
      LogInfo.logs("tags = %s", Grammar.opts.tags);
      Grammar grammar = new Grammar();
      grammar.read();
      setBaseParser(new Spec(grammar, finalSpec.extractor, finalSpec.executor, finalSpec.valueEvaluator));
    }
    LogInfo.end_track();
  }

  public void setBaseParser(Spec spec) {
    baseParser = new FloatingParser(spec);
  }

  @Override
  public ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts) {
    return baseParser.newParserState(params, ex, computeExpectedCounts);
  }

}
