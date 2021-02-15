package edu.stanford.nlp.sempre;

// This is the simplest evaluator, but exact match can sometimes be too harsh.
public class ExactValueEvaluator implements ValueEvaluator {
  public double getCompatibility(Value target, Value pred) {
    return target.equals(pred) ? 1 : 0;
  }
}
