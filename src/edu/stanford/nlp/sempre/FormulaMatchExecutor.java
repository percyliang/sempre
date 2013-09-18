package edu.stanford.nlp.sempre;

import java.util.Collections;

/**
 * Return a string representation of a formula as the value.  This enables
 * evaluation against exact match of logical forms.  This is overly stringent
 * right now.
 */
public class FormulaMatchExecutor extends Executor {
  public Response execute(Formula formula) {
    formula = Formulas.betaReduction(formula);
    return new Response(new ListValue(
        Collections.singletonList(
            (Value) new NameValue(formula.toLispTree().toString(), null))));
  }
}
