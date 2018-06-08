package edu.stanford.nlp.sempre;

import com.google.common.base.Function;

import java.util.List;

/**
 * A PrimitiveFormula represents an atomic value which is cannot be decomposed
 * into further symbols.  Either a ValueFormula or a VariableFormula.
 *
 * @author Percy Liang
 */
public abstract class PrimitiveFormula extends Formula {

  @Override
  public void forEach(Function<Formula, Boolean> func) {
    func.apply(this);
  }

  @Override
  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? this : result;
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> func, boolean alwaysRecurse) {
    return func.apply(this);
  }
}
