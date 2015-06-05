package edu.stanford.nlp.sempre;

import java.util.ArrayList;
import java.util.List;
import fig.basic.*;

/**
 * Maps a string to a number (double).
 *
 * @author Percy Liang
 */
public class NumberFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "Omit units") public boolean unitless = false;
    @Option(gloss = "Also test numbers by try converting to float (instead of using NER tags)")
    public boolean alsoTestByConversion = false;
  }
  public static Options opts = new Options();

  private List<String> requests;  // List of types of fields to get (e.g., NUMBER)

  private boolean request(String req) {
    return requests == null || requests.contains(req);
  }

  public void init(LispTree tree) {
    super.init(tree);
    if (tree.children.size() > 1) {
      requests = new ArrayList<String>();
      for (int i = 1; i < tree.children.size(); i++)
        requests.add(tree.child(1).value);
    }
  }

  // TODO(pliang): handle measurements too (e.g., 3cm)
  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      public Derivation createDerivation() {
        // Numbers: If it is an integer, set its type to integer.  Otherwise, use float.
        if (request("NUMBER")) {
          String value = ex.languageInfo.getNormalizedNerSpan("NUMBER", c.getStart(), c.getEnd());
          if (value != null) {
            try {
              NumberValue numberValue = new NumberValue(Double.parseDouble(value));
              SemType type = numberValue.value == (int) numberValue.value ? SemType.intType : SemType.floatType;
              return new Derivation.Builder()
                      .withCallable(c)
                      .formula(new ValueFormula<>(numberValue))
                      .type(type)
                      .createDerivation();
            } catch (NumberFormatException e) {
              LogInfo.warnings("NumberFn: Cannot convert NerSpan \"%s\" to a number", value);
            }
          }
        }

        // Ordinals
        if (request("ORDINAL")) {
          String value = ex.languageInfo.getNormalizedNerSpan("ORDINAL", c.getStart(), c.getEnd());
          if (value != null) {
            try {
              NumberValue numberValue = (opts.unitless ?
                  new NumberValue(Double.parseDouble(value)) :
                    new NumberValue(Double.parseDouble(value), "fb:en.ordinal_number"));
              SemType type = SemType.intType;
              return new Derivation.Builder()
                      .withCallable(c)
                      .formula(new ValueFormula<>(numberValue))
                      .type(type)
                      .createDerivation();
            } catch (NumberFormatException e) {
              LogInfo.warnings("NumberFn: Cannot convert NerSpan \"%s\" to a number", value);
            }
          }
        }

        // Percents
        if (request("PERCENT")) {
          String value = ex.languageInfo.getNormalizedNerSpan("PERCENT", c.getStart(), c.getEnd());
          if (value != null) {
            try {
              NumberValue numberValue = (opts.unitless ?
                  new NumberValue(Double.parseDouble(value.substring(1))) :
                    new NumberValue(0.01 * Double.parseDouble(value.substring(1))));
              SemType type = SemType.floatType;
              return new Derivation.Builder()
                      .withCallable(c)
                      .formula(new ValueFormula<>(numberValue))
                      .type(type)
                      .createDerivation();
            } catch (NumberFormatException e) {
              LogInfo.warnings("NumberFn: Cannot convert NerSpan \"%s\" to a number", value);
            }
          }
        }

        // Money
        if (request("MONEY")) {
          String value = ex.languageInfo.getNormalizedNerSpan("MONEY", c.getStart(), c.getEnd());
          if (value != null) {
            try {
              NumberValue numberValue = (opts.unitless ?
                  new NumberValue(Double.parseDouble(value.substring(1))) :
                    new NumberValue(Double.parseDouble(value.substring(1)), "fb:en.dollar"));
              SemType type = SemType.floatType;
              return new Derivation.Builder()
                      .withCallable(c)
                      .formula(new ValueFormula<>(numberValue))
                      .type(type)
                      .createDerivation();
            } catch (NumberFormatException e) {
              LogInfo.warnings("NumberFn: Cannot convert NerSpan \"%s\" to a number", value);
            }
          }
        }

        // Test by converting string to number directly (don't look at NER)
        if (opts.alsoTestByConversion && request("NUMBER") & c.getEnd() - c.getStart() == 1) {
          String value = ex.languageInfo.tokens.get(c.getStart());
          if (value != null) {
            try {
              NumberValue numberValue = new NumberValue(Double.parseDouble(value));
              SemType type = numberValue.value == (int) numberValue.value ? SemType.intType : SemType.floatType;
              return new Derivation.Builder()
                      .withCallable(c)
                      .formula(new ValueFormula<>(numberValue))
                      .type(type)
                      .createDerivation();
            } catch (NumberFormatException e) {
              // Don't issue warnings; most spans are not numbers
            }
          }
        }

        return null;
      }
    };
  }
}
