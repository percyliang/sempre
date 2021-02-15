package edu.stanford.nlp.sempre;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse strings representing date ranges such as "20th century".
 * The result has the form (and (< ...) (>= ...)).
 * 
 * Currently only supports patterns like "1990's" and "1800s"
 *
 * @author ppasupat
 */
public class DateRangeFn extends SemanticFn {

  @Override
  public DerivationStream call(Example ex, Callable c) {
    return new LazyDateRangeFnDerivs(ex, c);
  }

  public static class LazyDateRangeFnDerivs extends MultipleDerivationStream {
    Example ex;
    Callable c;

    int index = 0;
    List<Formula> formulas;

    public LazyDateRangeFnDerivs(Example ex, Callable c) {
      this.ex = ex;
      this.c = c;
    }

    @Override
    public Derivation createDerivation() {
      if (formulas == null)
        populateFormulas();

      if (index >= formulas.size()) return null;
      Formula formula = formulas.get(index++);

      return new Derivation.Builder().withCallable(c)
          .formula(formula).type(SemType.numberType).createDerivation();
    }

    private static final Pattern YEAR_RANGE = Pattern.compile("^(\\d+0+)\\s*'?s$");

    // TODO: Handle more cases
    private void populateFormulas() {
      formulas = new ArrayList<>();
      String query = c.childStringValue(0);
      Matcher matcher = YEAR_RANGE.matcher(query);
      if (!matcher.matches()) return;
      int year = Integer.parseInt(matcher.group(1)), range = 10;
      while (year % range == 0) {
        // Put "<" before ">=" to keep the children of MergeFormula sorted
        formulas.add(new MergeFormula(MergeFormula.Mode.and,
            new JoinFormula(new ValueFormula<Value>(new NameValue("<")), new ValueFormula<>(new NumberValue(year + range))),
            new JoinFormula(new ValueFormula<Value>(new NameValue(">=")), new ValueFormula<>(new NumberValue(year)))));
        range *= 10;
      }
    }
  }
}
