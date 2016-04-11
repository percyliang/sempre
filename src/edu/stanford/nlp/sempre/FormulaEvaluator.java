package edu.stanford.nlp.sempre;

/**
 * Given an Example containing multiple target formulas
 * and a predicted formula, return a compatibility.
 */
public interface FormulaEvaluator extends ValueEvaluator {
  // Return a number [0, 1] denoting the compatibility.
  double getCompatibility(Example target, Derivation deriv);
}
