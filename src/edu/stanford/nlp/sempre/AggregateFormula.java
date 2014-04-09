package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

/**
 * Aggregate takes a set and computes some number of that set.
 * Includes existential quantification.
 *
 * @author Percy Liang
 */
public class AggregateFormula extends Formula {
  public enum Mode {count, sum, mean, min, max, exists};
  public final Mode mode;
  public final Formula child;

  public AggregateFormula(Mode mode, Formula child) {
    this.mode = mode;
    this.child = child;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild(mode.toString());
    tree.addChild(child.toLispTree());
    return tree;
  }

  public static Mode parseMode(String mode) {
    if ("count".equals(mode)) return Mode.count;
    if ("sum".equals(mode)) return Mode.sum;
    if ("mean".equals(mode)) return Mode.mean;
    if ("min".equals(mode)) return Mode.min;
    if ("max".equals(mode)) return Mode.max;
    if ("exists".equals(mode)) return Mode.max;
    return null;
  }

  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new AggregateFormula(mode, child.map(func)) : result;
  }

  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof AggregateFormula)) return false;
    AggregateFormula that = (AggregateFormula) thatObj;
    if (!this.mode.equals(that.mode)) return false;
    if (!this.child.equals(that.child)) return false;
    return true;
  }
  
  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + mode.toString().hashCode();
    hash = hash * 0xd3a2646c + child.hashCode();
    return hash;
  }
}
