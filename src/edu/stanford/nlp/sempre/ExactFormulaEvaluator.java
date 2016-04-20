package edu.stanford.nlp.sempre;

/**
 * If the example contains a targetFormula, compare against the targetFormula.
 * Otherwise, back off to targetValue.
 *
 * @author ppasupat
 */
public class ExactFormulaEvaluator extends ExactValueEvaluator implements FormulaEvaluator {
  @Override
  public double getCompatibility(Example ex, Derivation deriv) {
    if (ex.targetFormula != null)
      return (ex.targetFormula.equals(deriv.formula) ? 1 : 0);
    return getCompatibility(ex.targetValue, deriv.value);
  }
}
