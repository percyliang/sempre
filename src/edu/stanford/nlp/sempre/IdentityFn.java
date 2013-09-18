package edu.stanford.nlp.sempre;

import java.util.Collections;
import java.util.List;

/**
 * Identity function.
 *
 * @author Percy Liang
 */
public class IdentityFn extends SemanticFn {
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o != null && getClass() == o.getClass();
  }

  public List<Derivation> call(Example ex, Callable c) {
    if (c.getChildren().size() != 1)
      throw new RuntimeException("Bad args: " + c.getChildren());
    return Collections.singletonList(
        new Derivation.Builder()
            .withCallable(c)
            .withFormulaFrom(c.child(0))
            .createDerivation());
  }
}
