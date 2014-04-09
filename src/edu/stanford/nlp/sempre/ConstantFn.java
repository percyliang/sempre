package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.Collections;
import java.util.List;

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
      this.type = crudeInferType(formula);
    }
  }

  private SemType crudeInferType(Formula formula) {
    // Try to infer the type
    if (formula instanceof ValueFormula) {
      Value value = ((ValueFormula)formula).value;
      if (value instanceof NumberValue) return SemType.numberType;
      else if (value instanceof StringValue) return SemType.stringType;
      else if (value instanceof DateValue) return SemType.dateType;
      else if (value instanceof NameValue) return SemType.entityType;
    } else if (formula instanceof LambdaFormula) {
      return new FuncSemType(SemType.topType, crudeInferType(((LambdaFormula)formula).body));
    }

    throw new RuntimeException("Can't infer type of " + formula);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConstantFn that = (ConstantFn) o;
    if (!formula.equals(that.formula)) return false;
    if (!type.equals(that.type)) return false;
    return true;
  }

  public List<Derivation> call(Example ex, Callable c) {
    Derivation deriv = new Derivation.Builder()
        .withCallable(c)
        .formula(formula)
        .type(type)
        .createDerivation();
    if (FeatureExtractor.containsDomain("constant"))
      deriv.addFeature("constant", ex.phraseString(c.getStart(), c.getEnd()) + " --- " + formula.toString());
    return Collections.singletonList(deriv);
  }
}
