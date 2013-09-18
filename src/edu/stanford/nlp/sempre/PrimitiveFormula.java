package edu.stanford.nlp.sempre;

import com.google.common.base.Function;

/**
 * A PrimitiveFormula represents an atomic value which is cannot be decomposed
 * into further symbols.  Either a ValueFormula or a VariableFormula.
 *
 * @author Percy Liang
 */
public abstract class PrimitiveFormula extends Formula {
  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? this : result;
  }
}
