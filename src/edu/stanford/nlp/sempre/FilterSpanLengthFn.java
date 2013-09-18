package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.Collections;
import java.util.List;

public class FilterSpanLengthFn extends SemanticFn {

  private int minLength;

  public void init(LispTree tree) {
    super.init(tree);
    minLength = Integer.parseInt(tree.child(1).value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilterSpanLengthFn that = (FilterSpanLengthFn) o;
    if (minLength != that.minLength) return false;
    return true;
  }

  @Override
  public List<Derivation> call(Example ex, Callable c) {
    if (c.getEnd() - c.getStart() < minLength)
      return Collections.emptyList();
    return Collections.singletonList(
        new Derivation.Builder()
            .withCallable(c)
            .withFormulaFrom(c.child(0))
            .createDerivation());
  }

}
