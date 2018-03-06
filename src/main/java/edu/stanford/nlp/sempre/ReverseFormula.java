package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

import java.util.List;

/**
 * If |expr| denotes a set of pairs S,
 * then (reverse |expr|) denotes the set of pairs {(y, x) : (x, y) \in S}.
 * Example:
 *   (reverse fb:people.person.date_of_birth)
 *   (reverse (lambda x (fb:location.statistical_region.population (fb:measurement_unit.dated_integer.number (var x)))))
 *
 * @author Percy Liang
 */
public class ReverseFormula extends Formula {
  public final Formula child;

  public ReverseFormula(Formula child) { this.child = child; }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("reverse");
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
    return result == null ? new ReverseFormula(child.map(func)) : result;
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
    if (!(thatObj instanceof ReverseFormula)) return false;
    ReverseFormula that = (ReverseFormula) thatObj;
    if (!this.child.equals(that.child)) return false;
    return true;
  }

  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + child.hashCode();
    return hash;
  }
}
