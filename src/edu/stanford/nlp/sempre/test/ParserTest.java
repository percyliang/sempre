package edu.stanford.nlp.sempre.test;

import edu.stanford.nlp.sempre.*;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Test parsers.
 *
 * @author Roy Frostig
 * @author Percy Liang
 */
public class ParserTest {
  public static BeamParser makeSimpleBeamParser() {
    Executor executor = new FormulaMatchExecutor();
    FeatureExtractor extractor = new FeatureExtractor(executor);
    FeatureExtractor.opts.featureDomains.add("rule");
    return new BeamParser(TestUtils.makeArithmeticGrammar(), extractor, executor);
  }

  private static void checkNumDerivations(Parser parser, String utterance, int numExpected) {
    Params params = new Params();
    Example ex = TestUtils.makeSimpleExample(utterance);
    parser.parse(params, ex);
    assertEquals(numExpected, ex.getPredDerivations().size());
  }

  private static void checkNumDerivations(Parser parser) {
    checkNumDerivations(parser, "1 +", 0);
    checkNumDerivations(parser, "1", 1);
    checkNumDerivations(parser, "1 2", 1);
    checkNumDerivations(parser, "1 2 3", 2);
    checkNumDerivations(parser, "10 20 30 40 50 60", 42);
  }

  @Test
  public void checkNumDerivations() {
    LanguageInfo.opts.useAnnotators = false;
    checkNumDerivations(makeSimpleBeamParser());
    LanguageInfo.opts.useAnnotators = true;
    // Include other parsers here...
  }
}
