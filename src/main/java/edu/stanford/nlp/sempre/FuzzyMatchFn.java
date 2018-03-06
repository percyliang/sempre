package edu.stanford.nlp.sempre;

import java.util.*;

import fig.basic.*;

/**
 * Similar to LexiconFn, but list all approximate matches from a FuzzyMatchable instance.
 *
 * @author ppasupat
 */
public class FuzzyMatchFn extends SemanticFn {
  public static class Options {
    @Option public int verbose = 0;
  }
  public static Options opts = new Options();

  public enum FuzzyMatchFnMode { UNARY, BINARY, ENTITY,
    ORDER_BEFORE, ORDER_AFTER, ORDER_NEXT, ORDER_PREV, ORDER_ADJACENT };
  private FuzzyMatchFnMode mode;

  // Generate all possible denotations regardless of the phrase
  private boolean matchAny = false;

  public void init(LispTree tree) {
    super.init(tree);
    for (int i = 1; i < tree.children.size(); i++) {
      String value = tree.child(i).value;
      if ("unary".equals(value)) this.mode = FuzzyMatchFnMode.UNARY;
      else if ("binary".equals(value)) this.mode = FuzzyMatchFnMode.BINARY;
      else if ("entity".equals(value)) this.mode = FuzzyMatchFnMode.ENTITY;
      else if ("any".equals(value)) this.matchAny = true;
      else if ("before".equals(value)) this.mode = FuzzyMatchFnMode.ORDER_BEFORE;
      else if ("after".equals(value)) this.mode = FuzzyMatchFnMode.ORDER_AFTER;
      else if ("next".equals(value)) this.mode = FuzzyMatchFnMode.ORDER_NEXT;
      else if ("prev".equals(value)) this.mode = FuzzyMatchFnMode.ORDER_PREV;
      else if ("adjacent".equals(value)) this.mode = FuzzyMatchFnMode.ORDER_ADJACENT;
      else throw new RuntimeException("Invalid argument: " + value);
    }
  }

  public FuzzyMatchFnMode getMode() { return mode; }
  public boolean getMatchAny() { return matchAny; }

  @Override
  public DerivationStream call(Example ex, Callable c) {
    return new LazyFuzzyMatchFnDerivs(ex, c, mode, matchAny);
  }

  // ============================================================
  // Derivation Stream
  // ============================================================

  public static class LazyFuzzyMatchFnDerivs extends MultipleDerivationStream {
    final Example ex;
    final FuzzyMatchable matchable;
    final Callable c;
    final String query;
    final List<String> sentence;
    final FuzzyMatchFnMode mode;
    final boolean matchAny;

    int index = 0;
    List<Formula> formulas;

    public LazyFuzzyMatchFnDerivs(Example ex, Callable c, FuzzyMatchFnMode mode, boolean matchAny) {
      this.ex = ex;
      if (ex.context != null && ex.context.graph != null && ex.context.graph instanceof FuzzyMatchable)
        this.matchable = (FuzzyMatchable) ex.context.graph;
      else
        this.matchable = null;
      this.c = c;
      this.query = (matchAny || c.getChildren().isEmpty()) ? null : c.childStringValue(0);
      if (c.getRule().rhs.size() == 1 && Rule.phraseCat.equals(c.getRule().rhs.get(0))) {
        sentence = ex.getTokens();
      } else if (c.getRule().rhs.size() == 1 && Rule.lemmaPhraseCat.equals(c.getRule().rhs.get(0))) {
        sentence = ex.getLemmaTokens();
      } else {
        sentence = null;
      }
      this.mode = mode;
      this.matchAny = matchAny;
      if (opts.verbose >= 2)
        LogInfo.logs("FuzzyMatchFn[%s]%s.call: %s",
            this.mode, (this.matchAny ? "[matchAny]" : ""), this.query);
    }

    @Override
    public Derivation createDerivation() {
      if (matchable == null) return null;
      if (query == null && !matchAny) return null;

      // Compute the formulas if not computed yet
      if (formulas == null) {
        if (matchAny)
          formulas = new ArrayList<>(matchable.getAllFormulas(mode));
        else if (sentence != null)
          formulas = new ArrayList<>(matchable.getFuzzyMatchedFormulas(sentence, c.getStart(), c.getEnd(), mode));
        else
          formulas = new ArrayList<>(matchable.getFuzzyMatchedFormulas(query, mode));
      }

      // Use the next formula to create a derivation
      if (index >= formulas.size()) return null;
      Formula formula = formulas.get(index++);
      SemType type = TypeInference.inferType(formula);

      FeatureVector features = new FeatureVector();
      if (FeatureExtractor.containsDomain("fuzzyMatch")) {
        features.add("fuzzyMatch", "mode=" + mode);
        if (matchAny)
          features.add("fuzzyMatch", "mode=" + mode + "_any");
      }

      return new Derivation.Builder()
          .withCallable(c)
          .formula(formula)
          .type(type)
          .localFeatureVector(features)
          .createDerivation();
    }

  }

}
