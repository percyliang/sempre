package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

import java.util.List;

/**
 * A join formula represents a database join and has the following form:
 *   (relation child)
 * If |relation| is a ValueFormula<NameValue>, then the formula is equivalent
 * to the following in lambda calculus:
 *   (lambda x (exists y (and (relation y x) (child y))))
 * If |relation| is a LambdaFormula, then (relation child) is just applying the
 * lambda expression |relation| to the argument |child|.
 *
 * @author Percy Liang
 */
public class JoinFormula extends Formula {
  public final Formula relation;
  public final Formula child;

  public JoinFormula(String relation, Formula child) {
    this(Formulas.newNameFormula(relation), child);
  }

  public JoinFormula(Formula relation, Formula child) {
    this.relation = relation;
    this.child = child;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild(relation.toLispTree());
    tree.addChild(child.toLispTree());
    return tree;
  }

  @Override
  public void forEach(Function<Formula, Boolean> func) {
    if (!func.apply(this)) { relation.forEach(func); child.forEach(func); }
  }

  @Override
  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new JoinFormula(relation.map(func), child.map(func)) : result;
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> func, boolean alwaysRecurse) {
    List<Formula> res = func.apply(this);
    if (res.isEmpty() || alwaysRecurse) {
      res.addAll(relation.mapToList(func, alwaysRecurse));
      res.addAll(child.mapToList(func, alwaysRecurse));
    }
    return res;
  }

  @SuppressWarnings({"equalshashcode"})
  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof JoinFormula)) return false;
    JoinFormula that = (JoinFormula) thatObj;
    if (!this.relation.equals(that.relation)) return false;
    if (!this.child.equals(that.child)) return false;
    return true;
  }

  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + relation.hashCode();
    hash = hash * 0xd3a2646c + child.hashCode();
    return hash;
  }
}
