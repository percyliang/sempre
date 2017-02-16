package edu.stanford.nlp.sempre.tables.features;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Function;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.ScopedFormula;
import edu.stanford.nlp.sempre.tables.serialize.LazyLoadedExampleList;
import fig.basic.LogInfo;
import fig.basic.Option;

/**
 * Extract features of the form (n-gram, logical form fragment)
 *
 * @author ppasupat
 */
public class PhraseFragmentFeatureComputer implements FeatureComputer {
  public static class Options {
    @Option(gloss = "verbosity")
    public int verbose = 0;
    @Option(gloss = "number of recursion levels")
    public int recursionLevels = 1;
    @Option(gloss = "Define unlexicalized phrase-fragment features")
    public boolean unlexicalizedPhraseFragment = true;
    @Option(gloss = "Define lexicalized phrase-fragment features")
    public boolean lexicalizedPhraseFragment = true;
    @Option(gloss = "Forbid type of predicate (%row.row) that comes alone")
    public boolean forbidSingletonPredicateType = true;
  }
  public static Options opts = new Options();
  
  @Override public void setExecutor(Executor executor) { }    // Do nothing

  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("phrase-fragment")) return;
    List<PhraseInfo> phraseInfos = PhraseInfo.getPhraseInfos(ex);
    extractPhraseFragment(ex, deriv, phraseInfos);
  }

  private void extractPhraseFragment(Example ex, Derivation deriv, List<PhraseInfo> phraseInfos) {
    Formula formula = deriv.formula;
    if (opts.verbose >= 1) {
      List<Formula> children = new ArrayList<>();
      for (Derivation child : deriv.children)
        children.add(child.formula);
      LogInfo.logs("%s => %s", formula, children);
    }
    // For formulas from SerializedParser, we need to define all features in one pass
    if (LazyLoadedExampleList.SERIALIZED_ROOT.equals(deriv.cat)) {
      extractPhraseFragment(ex, deriv, phraseInfos, formula, opts.recursionLevels, "<R>@ROOT");
      formula.forEach(new Function<Formula, Boolean>() {
        @Override
        public Boolean apply(Formula subformula) {
          if (!(subformula instanceof ReverseFormula || subformula instanceof LambdaFormula))
            extractPhraseFragment(ex, deriv, phraseInfos, subformula, opts.recursionLevels, "<R>");
          return false;
        }
      });
      return;
    }
    // Special for ROOT
    if (Rule.rootCat.equals(deriv.cat)) {
      extractPhraseFragment(ex, deriv, phraseInfos, formula, opts.recursionLevels, "<R>@ROOT");
    }
    // Check type raising (formula == the single child); do not define features here
    if (deriv.children.size() == 1 && formula.equals(deriv.children.get(0).formula)) {
      if (opts.verbose >= 1)
        LogInfo.logs("TYPE RAISE [%s]: %s", deriv.rule, formula);
      return;
    }
    // Define features based on the formula type
    extractPhraseFragment(ex, deriv, phraseInfos, formula, opts.recursionLevels, "<R>");
  }

  private void extractPhraseFragment(Example ex, Derivation deriv, List<PhraseInfo> phraseInfos, Formula formula,
      int level, String fragment, Value... placeholders) {
    if (level < 0)
      throw new RuntimeException("Level = " + level + " < 0");
    // Look at the formula type
    int N = placeholders.length;
    if (formula instanceof ValueFormula) {
      Value value = ((ValueFormula<?>) formula).value;
      if (value instanceof NumberValue) {
        if (!opts.forbidSingletonPredicateType || !"<R>".equals(fragment))
          extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "(number *)"), placeholders);
        extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "#" + N), C(placeholders, value));

      } else if (value instanceof DateValue) {
        if (!opts.forbidSingletonPredicateType || !"<R>".equals(fragment))
          extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "(date *)"), placeholders);
        extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "#" + N), C(placeholders, value));

      } else if (value instanceof NameValue) {
        if (!opts.forbidSingletonPredicateType || !"<R>".equals(fragment))
          extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, getType((NameValue) value)), placeholders);
        extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "#" + N), C(placeholders, value));

      }

    } else if (formula instanceof JoinFormula) {
      JoinFormula join = (JoinFormula) formula;
      Formula relation = join.relation;
      if (relation instanceof ReverseFormula && ((ReverseFormula) relation).child instanceof ValueFormula) {
        Value relationValue = ((ValueFormula<?>) ((ReverseFormula) relation).child).value;
        extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "(!#" + N + " *)"), C(placeholders, relationValue));
        if (level > 0)
          extractPhraseFragment(ex, deriv, phraseInfos, join.child, level - 1, R(fragment, "(!#" + N + " <R>)"), C(placeholders, relationValue));

      } else if (relation instanceof ValueFormula) {
        Value relationValue = ((ValueFormula<?>) relation).value;
        extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "(#" + N + " *)"), C(placeholders, relationValue));
        if (level > 0)
          extractPhraseFragment(ex, deriv, phraseInfos, join.child, level - 1, R(fragment, "(#" + N + " <R>)"), C(placeholders, relationValue));

      } else {
        throw new RuntimeException("[Phrase-Fragment] Unrecognized JoinFormula: " + formula);
      }

    } else if (formula instanceof MergeFormula) {
      MergeFormula merge = (MergeFormula) formula;
      extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "(" + merge.mode + " * *)"), placeholders);
      if (level > 0) {
        extractPhraseFragment(ex, deriv, phraseInfos, merge.child1, level - 1, R(fragment, "(" + merge.mode + " <R> *)"), placeholders);
        extractPhraseFragment(ex, deriv, phraseInfos, merge.child2, level - 1, R(fragment, "(" + merge.mode + " <R> *)"), placeholders);
      }

    } else if (formula instanceof AggregateFormula) {
      AggregateFormula aggregate = (AggregateFormula) formula;
      extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "(" + aggregate.mode + " *)"), placeholders);
      if (level > 0)
        extractPhraseFragment(ex, deriv, phraseInfos, aggregate.child, level - 1, R(fragment, "(" + aggregate.mode + " <R>)"), placeholders);

    } else if (formula instanceof SuperlativeFormula) {
      SuperlativeFormula superlative = (SuperlativeFormula) formula;
      extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "(" + superlative.mode + " * *)"), placeholders);
      if (level > 0) {
        extractPhraseFragment(ex, deriv, phraseInfos, superlative.head, level - 1, R(fragment, "(" + superlative.mode + " <R> *)"), placeholders);
        if (superlative.relation instanceof ReverseFormula && ((ReverseFormula) superlative.relation).child instanceof LambdaFormula) {
          // (argmax 1 1 * (reverse (lambda (var x) ...)))
          Formula lambdaBody = ((LambdaFormula) ((ReverseFormula) superlative.relation).child).body;
          extractPhraseFragment(ex, deriv, phraseInfos, lambdaBody, level - 1, R(fragment, "(" + superlative.mode + " * R<R>)"), placeholders);
        } else if (superlative.relation instanceof LambdaFormula) {
          // (argmax 1 1 * (lambda (var x) ...))
          Formula lambdaBody = ((LambdaFormula) superlative.relation).body;
          extractPhraseFragment(ex, deriv, phraseInfos, lambdaBody, level - 1, R(fragment, "(" + superlative.mode + " * L<R>)"), placeholders);
        } else {
          // (argmax 1 1 * ...), such as (argmax 1 1 * @index)
          extractPhraseFragment(ex, deriv, phraseInfos, superlative.relation, level - 1, R(fragment, "(" + superlative.mode + " * <R>)"), placeholders);
        }
      }

    } else if (formula instanceof ArithmeticFormula) {
      ArithmeticFormula arithmetic = (ArithmeticFormula) formula;
      extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "(" + arithmetic.mode + " * *)"), placeholders);
      if (level > 0) {
        extractPhraseFragment(ex, deriv, phraseInfos, arithmetic.child1, level - 1, R(fragment, "(" + arithmetic.mode + " <R> *)"), placeholders);
        extractPhraseFragment(ex, deriv, phraseInfos, arithmetic.child2, level - 1, R(fragment, "(" + arithmetic.mode + " <R> *)"), placeholders);
      }

    } else if (formula instanceof VariableFormula) {
      extractPhraseFragment(ex, deriv, phraseInfos, R(fragment, "%x"), placeholders);

    } else if (formula instanceof ScopedFormula) {
      ScopedFormula scoped = (ScopedFormula) formula;
      if (level > 0) {
        extractPhraseFragment(ex, deriv, phraseInfos, scoped.head, level - 1, R(fragment, "(scoped <R> *)"), placeholders);
        if (scoped.relation instanceof LambdaFormula) {
          Formula lambdaBody = ((LambdaFormula) scoped.relation).body;
          extractPhraseFragment(ex, deriv, phraseInfos, lambdaBody, level - 1, R(fragment, "(scoped * L<R>)"), placeholders);
        } else {
          extractPhraseFragment(ex, deriv, phraseInfos, scoped.relation, level - 1, R(fragment, "(scoped * <R>)"), placeholders);
        }
      }

    } else if (formula instanceof MarkFormula) {
      // TODO: Handle mark formula
    } else if (formula instanceof ReverseFormula) {
      throw new RuntimeException("[Phrase-Fragment] ReverseFormula not handled: " + formula);
    } else if (formula instanceof LambdaFormula) {
      throw new RuntimeException("[Phrase-Fragment] LambdaFormula not handled: " + formula);
    } else {
      throw new RuntimeException("[Phrase-Fragment] Cannot handle formula " + formula);
    }
  }

  private String R(String fragment, String recursedFragment) {
    return fragment.replace("<R>", recursedFragment);
  }

  private Value[] C(Value[] oldValues, Value newValue) {
    Value[] combined = new Value[oldValues.length + 1];
    for (int i = 0; i < oldValues.length; i++) combined[i] = oldValues[i];
    combined[oldValues.length] = newValue;
    return combined;
  }

  private static final Pattern UNARY_PATTERN = Pattern.compile("^fb:([^.]*)\\.[^.]*$");
  private static final Pattern BINARY_PATTERN = Pattern.compile("^fb:([^.]*\\.[^.]*)\\.[^.]*$");

  private String getType(NameValue name) {
    String id = name.id;
    if (CanonicalNames.COMPARATORS.contains(id)) return "%COMP";
    if (CanonicalNames.COLON.equals(id)) return "%COLON";
    if (CanonicalNames.isUnary(id)) {
      Matcher matcher = UNARY_PATTERN.matcher(id);
      if (matcher.matches()) return "%" + matcher.group(1);
    } else if (CanonicalNames.isBinary(id)) {
      Matcher matcher = BINARY_PATTERN.matcher(id);
      if (matcher.matches()) return "%" + matcher.group(1);
    }
    throw new RuntimeException("[getType] Unhandled NameValue: " + name);
  }

  private void extractPhraseFragment(Example ex, Derivation deriv, List<PhraseInfo> phraseInfos, String fragment, Value... placeholders) {
    if (opts.verbose >= 1)
      LogInfo.logs(">>> %s %s", fragment, Arrays.asList(placeholders));
    String[] placeholderStrings = new String[placeholders.length],
        originalStrings = new String[placeholders.length];
    for (int i = 0; i < placeholders.length; i++) {
      Value placeholder = placeholders[i];
      if (placeholder instanceof NameValue) {
        placeholderStrings[i] = ((NameValue) placeholder).id.replace("fb:", "");
        originalStrings[i] = PredicateInfo.getOriginalString(((NameValue) placeholder).id, ex);
      } else if (placeholder instanceof NumberValue) {
        originalStrings[i] = placeholderStrings[i] = "" + ((NumberValue) placeholder).value;
      } else if (placeholder instanceof DateValue) {
        originalStrings[i] = placeholderStrings[i] = ((DateValue) placeholder).isoString();
      } else {
        throw new RuntimeException("[placeholder] Unhandled Value: " + placeholder);
      }
    }
    String defaultFragment = fragment;
    for (int i = 0; i < placeholderStrings.length; i++) {
      defaultFragment = defaultFragment.replace("#" + i, placeholderStrings[i]);
    }
    String[] placeholderStringsUnlex = new String[placeholders.length];
    for (PhraseInfo phraseInfo : phraseInfos) {
      if (opts.lexicalizedPhraseFragment)
        deriv.addFeature("p-f", phraseInfo.lemmaText + ";" + defaultFragment);
      if (opts.unlexicalizedPhraseFragment) {
        boolean matched = false;
        for (int i = 0; i < placeholders.length; i++) {
          Value placeholder = placeholders[i];
          if (placeholder instanceof NameValue) {
            if (originalStrings[i] != null && originalStrings[i].equals(phraseInfo.lemmaText)) {
              placeholderStringsUnlex[i] = "$";
              matched = true;
            } else {
              placeholderStringsUnlex[i] = placeholderStrings[i];
            }
          } else if (placeholder instanceof NumberValue || placeholder instanceof DateValue) {
            if (originalStrings[i] != null && originalStrings[i].equals(phraseInfo.normalizedNerSpan)) {
              placeholderStringsUnlex[i] = "$";
              matched = true;
            } else {
              placeholderStringsUnlex[i] = placeholderStrings[i];
            }
          } else {
            throw new RuntimeException("[placeholder] Unhandled Value: " + placeholder);
          }
        }
        if (matched) {
          String unlexFragment = fragment;
          for (int i = 0; i < placeholderStrings.length; i++) {
            unlexFragment = unlexFragment.replace("#" + i, placeholderStringsUnlex[i]);
          }
          deriv.addFeature("p-fu", unlexFragment);
        }
      }
    }
  }
}
