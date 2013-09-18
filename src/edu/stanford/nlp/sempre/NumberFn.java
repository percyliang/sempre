package edu.stanford.nlp.sempre;

import java.util.Collections;
import java.util.List;

/**
 * Maps a string to a number (double).
 *
 * @author Percy Liang
 */
public class NumberFn extends SemanticFn {
  // TODO: handle measurements too (e.g., 3cm)

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o != null && getClass() == o.getClass();
  }

  public List<Derivation> call(Example ex, Callable c) {
    String value = ex.languageInfo.getNormalizedNerSpan("NUMBER", c.getStart(), c.getEnd());
    if (value == null) return Collections.emptyList();

    NumberValue numberValue = new NumberValue(Double.parseDouble(value));

    return Collections.singletonList(
        new Derivation.Builder()
            .withCallable(c)
            .formula(new ValueFormula<NumberValue>(numberValue))
            .type(SemType.numberType)
            .createDerivation());
  }
}
