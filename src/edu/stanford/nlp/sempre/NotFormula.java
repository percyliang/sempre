package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

import java.util.List;

/**
 * (not expression) returns the truth value which is opposite of expression.
 *
 * @author Percy Liang
 */
public class NotFormula extends Formula {
  public final Formula child;

  public NotFormula(Formula child) { this.child = child; }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("not");
    tree.addChild(child.toLispTree());
    return tree;
  }

  @Override
  public void forEach(Function<Formula, Boolean> func) {
    if (!func.apply(this)) child.forEach(func);
  }

  @Override
  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new NotFormula(child.map(func)) : result;
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> func, boolean alwaysRecurse) {
    List<Formula> res = func.apply(this);
    if (res.isEmpty() || alwaysRecurse)
      res.addAll(child.mapToList(func, alwaysRecurse));
    return res;
  }

  @SuppressWarnings({"equalshashcode"})
  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof NotFormula)) return false;
    NotFormula that = (NotFormula) thatObj;
    if (!this.child.equals(that.child)) return false;
    return true;
  }

  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + child.hashCode();
    return hash;
  }
}
