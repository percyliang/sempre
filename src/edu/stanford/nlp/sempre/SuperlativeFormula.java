package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

/**
 * Computes the extreme elements of a set |head| according to the degree given
 * by |relation|.
 *
 * @author Percy Liang
 */
class SuperlativeFormula extends Formula {
  public enum Mode {argmin, argmax};

  Mode mode;
  int rank;  // rank-th item
  int count;  // Number of items to fetch
  Formula head;
  Formula relation;  // Apply relation(head, degree) and sort by degree.

  public SuperlativeFormula(Mode mode, int rank, int count, Formula head, Formula relation) {
    this.mode = mode;
    this.rank = rank;
    this.count = count;
    this.head = head;
    this.relation = relation;
  }

  public static Mode parseMode(String mode) {
    if ("argmin".equals(mode)) return Mode.argmin;
    if ("argmax".equals(mode)) return Mode.argmax;
    return null;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild(mode + "");
    tree.addChild(rank + "");
    tree.addChild(count + "");
    tree.addChild(head.toLispTree());
    tree.addChild(relation.toLispTree());
    return tree;
  }

  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new SuperlativeFormula(mode, rank, count, head.map(func), relation.map(func)) : result;
  }

  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof SuperlativeFormula)) return false;
    SuperlativeFormula that = (SuperlativeFormula) thatObj;
    if (this.mode != that.mode) return false;
    if (this.rank != that.rank) return false;
    if (this.count != that.count) return false;
    if (!this.head.equals(that.head)) return false;
    if (!this.relation.equals(that.relation)) return false;
    return true;
  }
  @Override
  public int hashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + mode.toString().hashCode();
    hash = hash * 0xd3a2646c + rank;
    hash = hash * 0xd3a2646c + count;
    hash = hash * 0xd3a2646c + head.hashCode();
    hash = hash * 0xd3a2646c + relation.hashCode();
    return hash;
  }
}
