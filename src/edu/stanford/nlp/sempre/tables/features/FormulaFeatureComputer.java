package edu.stanford.nlp.sempre.tables.features;

import java.util.Random;

import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;

/**
 * Features on formulas.
 *
 * Originally written as baselines for correct formula evaluation.
 *
 * @author ppasupat
 */
public class FormulaFeatureComputer implements FeatureComputer {
  
  @Override public void setExecutor(Executor executor) { }    // Do nothing

  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    if (!deriv.isRoot(ex.numTokens())) return;
    if (FeatureExtractor.containsDomain("formula-random")) {
      // Uniform random value
      deriv.addFeature("formula-random", "random", new Random(deriv.formula.hashCode()).nextDouble());
    }
    if (FeatureExtractor.containsDomain("formula-length")) {
      // Formula length
      int length = findLength(Formulas.betaReduction(deriv.formula).toLispTree());
      deriv.addFeature("formula-length", "length", length);
    }
  }

  /**
   * Find the length of a formula. Contains discounts for some types of formulas.
   * (e.g., the (number 1) (number 1) in superlatives are ignored)
   */
  int findLength(LispTree tree) {
    if (tree.isLeaf()) {
      if ("reverse".equals(tree.value) || "var".equals(tree.value) || "lambda".equals(tree.value))
        return 0;
      return 1;
    }
    if ("argmax".equals(tree.child(0).value) || "argmin".equals(tree.child(0).value)) {
      int length = 1;
      for (int i = 3; i < tree.children.size(); i++)
        length += findLength(tree.child(i));
      return length;
    } else {
      int length = 0;
      for (LispTree child : tree.children)
        length += findLength(child);
      return length;
    }
  }



}
