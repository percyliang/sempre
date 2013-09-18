package edu.stanford.nlp.sempre;

import java.util.Collections;
import java.util.List;

/**
 * Maps a string to a Date.
 *
 * @author Percy Liang
 */
public class DateFn extends SemanticFn {
  @Deprecated public static List<String> dateAnnotations(String s) {
    return Collections.emptyList();
  }
  @Deprecated public static String dateAnnotation(String s) {
    throw new RuntimeException("Remove me");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o != null && getClass() == o.getClass();
  }

  public List<Derivation> call(Example ex, Callable c) {
    String value = ex.languageInfo.getNormalizedNerSpan("DATE", c.getStart(), c.getEnd());
    if (value == null) return Collections.emptyList();

    DateValue dateValue = DateValue.parseDateValue(value);
    if (dateValue == null) return Collections.emptyList();

    return Collections.singletonList(
        new Derivation.Builder()
            .withCallable(c)
            .formula(new ValueFormula<DateValue>(dateValue))
            .type(SemType.dateType)
            .createDerivation());
  }
}
