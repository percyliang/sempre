package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.Option;

import java.util.Collections;
import java.util.List;

/**
 * Takes two unaries and merges (takes the intersection) of them.
 *
 * @author Percy Liang
 */
public class MergeFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "whether to do a hard type-check")
    public boolean hardTypeCheck = true;
    @Option(gloss = "Verbose") public int verbose = 0;
  }

  private FbFormulasInfo fbFormulaInfo = null;
  public static Options opts = new Options();

  public MergeFn() {
    fbFormulaInfo = FbFormulasInfo.getSingleton();
  }

  MergeFormula.Mode mode;  // How to merge
  Formula formula;  // Optional: merge with this if exists

  public void init(LispTree tree) {
    super.init(tree);
    mode = MergeFormula.parseMode(tree.child(1).value);
    if (tree.children.size() == 3) {
      formula = Formulas.fromLispTree(tree.child(2));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MergeFn mergeFn = (MergeFn) o;
    if (formula != null ? !formula.equals(mergeFn.formula) : mergeFn.formula != null) return false;
    if (mode != mergeFn.mode) return false;
    return true;
  }

  public List<Derivation> call(Example ex, Callable c) {
    Formula result;
    if (c.getChildren().size() == 1)
      result = c.child(0).formula;
    else if (c.getChildren().size() == 2)
      result = new MergeFormula(mode, c.child(0).formula, c.child(1).formula);
    else
      throw new RuntimeException("Bad args: " + c.getChildren());

    // Compute resulting type
    Derivation child0 = c.child(0);
    Derivation child1 = c.child(1);
    SemType type = child0.type.meet(child1.type);
    FeatureVector features = new FeatureVector();

    if (!type.isValid()) {
      if (opts.hardTypeCheck)
        return Collections.emptyList();  // Don't accept logical forms that don't type check
      else {
        if (FeatureExtractor.containsDomain("typeCheck"))
          features.add("typeCheck", "mergeMismatch");  // Just add a feature
      }
    }

    if (formula != null)
      result = new MergeFormula(mode, formula, result);

    Derivation deriv = new Derivation.Builder()
        .withCallable(c)
        .formula(result)
        .type(type)
        .localFeatureVector(features)
        .createDerivation();

    if (SemanticFn.opts.trackLocalChoices) {
      deriv.localChoices.add(
          "MergeFn " +
              child0.startEndString(ex.getTokens()) + " " + child0.formula + " AND " +
              child1.startEndString(ex.getTokens()) + " " + child1.formula);
    }

    return Collections.singletonList(deriv);
  }
}
