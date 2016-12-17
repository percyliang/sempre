package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

import java.util.List;

/**
 * (filter x y): Find the set of all elements in x that function y evaluates to a non-empty set.   
 *
 * @author Panupong Pasupat
 */
public class FilterFormula extends Formula {
  public enum Mode { argmin, argmax };

  public final Formula domain;
  public final Formula condition;

  public FilterFormula(Formula domain, Formula condition) {
    this.domain = domain;
    this.condition = condition;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("filter");
    tree.addChild(domain.toLispTree());
    tree.addChild(condition.toLispTree());
    return tree;
  }

  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new FilterFormula(domain.map(func), condition.map(func)) : result;
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> func, boolean alwaysRecurse) {
    List<Formula> res = func.apply(this);
    if (res.isEmpty() || alwaysRecurse) {
      res.addAll(domain.mapToList(func, alwaysRecurse));
      res.addAll(condition.mapToList(func, alwaysRecurse));
    }
    return res;
  }

  @SuppressWarnings({"equalshashcode"})
  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof FilterFormula)) return false;
    FilterFormula that = (FilterFormula) thatObj;
    if (!this.domain.equals(that.domain)) return false;
    if (!this.condition.equals(that.condition)) return false;
    return true;
  }

  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + domain.hashCode();
    hash = hash * 0xd3a2646c + condition.hashCode();
    return hash;
  }
}

