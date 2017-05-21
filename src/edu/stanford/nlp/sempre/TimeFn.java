package edu.stanford.nlp.sempre;

/**
 * Maps a string to a Date.
 *
 * @author Percy Liang, Giovanni Campagna
 */
public class TimeFn extends SemanticFn {
  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        String value = ex.languageInfo.getNormalizedNerSpan("TIME", c.getStart(), c.getEnd());
        if (value == null)
          return null;
        DateValue dateValue = DateValue.parseDateValue(value);
        if (dateValue == null)
          return null;
        return new Derivation.Builder()
                .withCallable(c)
                .formula(new ValueFormula<>(dateValue))
                .type(SemType.dateType)
                .createDerivation();
      }
    };
  }
}
