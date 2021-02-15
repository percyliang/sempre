package edu.stanford.nlp.sempre.test;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import edu.stanford.nlp.sempre.*;
import fig.basic.Pair;
import fig.basic.Evaluation;

import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Various end-to-end sanity checks.
 *
 * @author Roy Frostig
 * @author Percy Liang
 */
public class SystemSanityTest {
  private static Builder makeBuilder(String grammarPath) {
    Grammar g = new Grammar();
    g.read(grammarPath);

    Builder b = new Builder();
    b.grammar = g;
    b.executor = new FormulaMatchExecutor();
    b.buildUnspecified();
    return b;
  }

  private static Dataset makeDataset() {
    Dataset d = new Dataset();
    d.readFromPathPairs(Collections.singletonList(
        Pair.newPair("train", "freebase/data/unittest-learn.examples")));
    return d;
  }

  private static Map<String, List<Evaluation>> learn(Builder builder, Dataset dataset) {
    Map<String, List<Evaluation>> evals = Maps.newHashMap();
    new Learner(builder.parser, builder.params, dataset).learn(3, evals);
    return evals;
  }

  @Test(groups = { "sparql", "corenlp" })
  public void easyEndToEnd() {
    LanguageAnalyzer.setSingleton(new SimpleAnalyzer());
    // Make sure learning works
    Dataset dataset = makeDataset();
    String[] grammarPaths = new String[] {
      "freebase/data/unittest-learn.grammar",
      "freebase/data/unittest-learn-ccg.grammar",
    };
    for (String grammarPath : grammarPaths) {
      Builder builder = makeBuilder(grammarPath);
      FeatureExtractor.opts.featureDomains.add("rule");
      Map<String, List<Evaluation>> evals = learn(builder, dataset);
      assertEquals(1.0d, Iterables.getLast(evals.get("train")).getFig("correct").min());
    }
  }
}
