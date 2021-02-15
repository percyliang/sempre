package edu.stanford.nlp.sempre;

/**
 * Given a target denotation Value and a predicted denotation Value,
 * return a compatibility.
 */
public interface ValueEvaluator {
  // Return a number [0, 1] that denotes how well we're doing.
  double getCompatibility(Value target, Value pred);
}
