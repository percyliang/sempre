package edu.stanford.nlp.sempre.tables;

import java.util.*;

import com.google.common.base.Function;

import edu.stanford.nlp.sempre.Formula;
import fig.basic.LispTree;

/**
 * Represent a binary with a restrict domain (scope).
 *
 * @author ppasupat
 */
public class ScopedFormula extends Formula {
  public final Formula head;
  public final Formula relation;

  public ScopedFormula(Formula head, Formula relation) {
    this.head = head;
    this.relation = relation;
  }

  @Override
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("scoped");
    tree.addChild(head.toLispTree());
    tree.addChild(relation.toLispTree());
    return tree;
  }

  @Override
  public void forEach(Function<Formula, Boolean> func) {
    if (!func.apply(this)) { head.forEach(func); relation.forEach(func); }
  }

  @Override
  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new ScopedFormula(head.map(func), relation.map(func)) : result;
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> func, boolean alwaysRecurse) {
    List<Formula> res = func.apply(this);
    if (res.isEmpty() || alwaysRecurse) {
      res.addAll(head.mapToList(func, alwaysRecurse));
      res.addAll(relation.mapToList(func, alwaysRecurse));
    }
    return res;
  }

  @SuppressWarnings({"equalshashcode"})
  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof ScopedFormula)) return false;
    ScopedFormula that = (ScopedFormula) thatObj;
    if (!this.head.equals(that.head)) return false;
    if (!this.relation.equals(that.relation)) return false;
    return true;
  }

  @Override
  public int computeHashCode() {
    int hash = 0x7e2a16;
    hash = hash * 0xd3b2646c + head.hashCode();
    hash = hash * 0xd3b2646c + relation.hashCode();
    return hash;
  }

}
