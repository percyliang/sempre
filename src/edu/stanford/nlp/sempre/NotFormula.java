package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

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

  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new NotFormula(child.map(func)) : result;
  }

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
