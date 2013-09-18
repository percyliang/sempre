package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.Collections;
import java.util.List;

/**
 * Takes two strings and returns their concatenation.
 *
 * @author Percy Liang
 */
public class ConcatFn extends SemanticFn {
  String delim;

  public void init(LispTree tree) {
    super.init(tree);
    delim = tree.child(1).value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConcatFn concatFn = (ConcatFn) o;
    if (!delim.equals(concatFn.delim)) return false;
    return true;
  }

  public List<Derivation> call(Example ex, Callable c) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < c.getChildren().size(); i++) {
      if (i > 0) out.append(delim);
      out.append(c.childStringValue(i));
    }
    return Collections.singletonList(
        new Derivation.Builder()
            .withCallable(c)
            .withStringFormulaFrom(out.toString())
            .createDerivation());
  }
}
