package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

import java.util.List;

/**
 * Computes the extreme elements of a set |head| according to the degree given
 * by |relation|.
 *
 * @author Percy Liang
 */
public class SuperlativeFormula extends Formula {
  public enum Mode { argmin, argmax };

  public final Mode mode;
  public final Formula rank;  // rank-th item
  public final Formula count;  // Number of items to fetch
  public final Formula head;
  public final Formula relation;  // Apply relation(head, degree) and sort by degree.

  public SuperlativeFormula(Mode mode, Formula rank, Formula count, Formula head, Formula relation) {
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
    tree.addChild(rank.toLispTree());
    tree.addChild(count.toLispTree());
    tree.addChild(head.toLispTree());
    tree.addChild(relation.toLispTree());
    return tree;
  }

  @Override
  public void forEach(Function<Formula, Boolean> func) {
    if (!func.apply(this)) { rank.forEach(func); count.forEach(func); head.forEach(func); relation.forEach(func); }
  }

  @Override
  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new SuperlativeFormula(mode, rank.map(func), count.map(func), head.map(func), relation.map(func)) : result;
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> func, boolean alwaysRecurse) {
    List<Formula> res = func.apply(this);
    if (res.isEmpty() || alwaysRecurse) {
      res.addAll(rank.mapToList(func, alwaysRecurse));
      res.addAll(count.mapToList(func, alwaysRecurse));
      res.addAll(head.mapToList(func, alwaysRecurse));
      res.addAll(relation.mapToList(func, alwaysRecurse));
    }
    return res;
  }

  @SuppressWarnings({"equalshashcode"})
  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof SuperlativeFormula)) return false;
    SuperlativeFormula that = (SuperlativeFormula) thatObj;
    if (this.mode != that.mode) return false;
    if (!this.rank.equals(that.rank)) return false;
    if (!this.count.equals(that.count)) return false;
    if (!this.head.equals(that.head)) return false;
    if (!this.relation.equals(that.relation)) return false;
    return true;
  }

  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + mode.toString().hashCode();
    hash = hash * 0xd3a2646c + rank.hashCode();
    hash = hash * 0xd3a2646c + count.hashCode();
    hash = hash * 0xd3a2646c + head.hashCode();
    hash = hash * 0xd3a2646c + relation.hashCode();
    return hash;
  }
}
