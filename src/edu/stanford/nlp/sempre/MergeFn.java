package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.Option;
import fig.basic.LogInfo;

/**
 * Takes two unaries and merges (takes the intersection) of them.
 *
 * @author Percy Liang
 */
public class MergeFn extends SemanticFn {
  public static class Options {
    @Option(gloss = "whether to do a hard type-check")
    public boolean hardTypeCheck = true;

    @Option public boolean showTypeCheckFailures = false;
    @Option(gloss = "Verbose") public int verbose = 0;
  }

  public static Options opts = new Options();

  MergeFormula.Mode mode;  // How to merge
  Formula formula;  // Optional: merge with this if exists

  public void init(LispTree tree) {
    super.init(tree);
    mode = MergeFormula.parseMode(tree.child(1).value);
    if (tree.children.size() == 3) {
      formula = Formulas.fromLispTree(tree.child(2));
    }
  }

  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
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
        if (opts.verbose >= 5)
          LogInfo.logs("MergeFn: %s | %s | %s", child0, child1, type);

        if (!type.isValid()) {
          if (opts.hardTypeCheck) {
            if (opts.showTypeCheckFailures)
              LogInfo.warnings("MergeFn: type check failed: [%s : %s] AND [%s : %s]", child0.formula, child0.type, child1.formula, child1.type);
            return null;
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
          deriv.addLocalChoice(
                  "MergeFn " +
                          child0.startEndString(ex.getTokens()) + " " + child0.formula + " AND " +
                          child1.startEndString(ex.getTokens()) + " " + child1.formula);
        }
        return deriv;
      }
    };
  }
}
