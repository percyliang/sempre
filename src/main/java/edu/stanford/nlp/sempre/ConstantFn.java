package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

/**
 * Just returns a fixed logical formula.
 *
 * @author Percy Liang
 */
public class ConstantFn extends SemanticFn {
  Formula formula;  // Formula to return
  SemType type;

  public ConstantFn() { }

  public ConstantFn(Formula formula) {
    init(LispTree.proto.newList("ConstantFn", formula.toLispTree()));
  }

  public void init(LispTree tree) {
    super.init(tree);
    this.formula = Formulas.fromLispTree(tree.child(1));
    if (2 < tree.children.size())
      this.type = SemType.fromLispTree(tree.child(2));
    else {
      this.type = TypeInference.inferType(formula);
    }
    if (!this.type.isValid())
      throw new RuntimeException("ConstantFn: " + formula + " does not type check");
  }

  public DerivationStream call(final Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        Derivation res = new Derivation.Builder()
                .withCallable(c)
                .formula(formula)
                .type(type)
                .createDerivation();
        // don't generate feature if it is not grounded to a string
        if (FeatureExtractor.containsDomain("constant") && c.getStart() != -1)
          res.addFeature("constant", ex.phraseString(c.getStart(), c.getEnd()) + " --- " + formula.toString());
        return res;
      }
    };
  }
}
