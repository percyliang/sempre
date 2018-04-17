package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Function;
import fig.basic.LispTree;

import java.util.List;

/**
 * A Formula is a logical form, which is the result of semantic parsing. Current
 * implementation is lambda calculus with primitives like description logic and
 * DCS to lessen the use of variables.
 * <p/>
 * Important note: define hashCode() for each Formula which only depends on the
 * value, not on random bits (don't include object IDs or enums).
 *
 * @author Percy Liang
 */
public abstract class Formula {
  // cache the hashcode
  private int hashCode = -1;
  // Serialize as LispTree.
  public abstract LispTree toLispTree();

  // Recursively perform some operation on each formula.
  // Apply to formulas.  If |func| returns false, then recurse on children.
  public abstract void forEach(Function<Formula, Boolean> func);

  // Recursively perform some operation on each formula.
  // Apply to formulas.  If |func| returns null, then recurse on children.
  public abstract Formula map(Function<Formula, Formula> func);

  // Recursively perform some operation on each formula.
  // Apply to formulas.  If |func| returns an empty set or |alwaysRecurse|, then recurse on children.
  public abstract List<Formula> mapToList(Function<Formula, List<Formula>> func, boolean alwaysRecurse);

  @JsonValue
  public String toString() { return toLispTree().toString(); }

  @JsonCreator
  public static Formula fromString(String str) {
    return Formulas.fromLispTree(LispTree.proto.parseFromString(str));
  }

  @Override public abstract boolean equals(Object o);
  @Override public int hashCode() {
    if (hashCode == -1)
      hashCode = computeHashCode();
    return hashCode;
  }

  public abstract int computeHashCode();

  public static Formula nullFormula = new PrimitiveFormula() {
      public LispTree toLispTree() { return LispTree.proto.newLeaf("null"); }
      @SuppressWarnings({"equalshashcode"})
      @Override public boolean equals(Object o) { return this == o; }
      public int computeHashCode() { return 0; }
  };
}
