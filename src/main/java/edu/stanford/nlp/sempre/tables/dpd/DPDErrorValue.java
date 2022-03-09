package edu.stanford.nlp.sempre.tables.dpd;

import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;

/**
 * Represent a partial formula (a lambda) obtained by applying the rule on the children.
 *
 * @author ppasupat
 */
public class DPDErrorValue extends Value {

  public final Rule rule;
  public final Value child1, child2;
  public final Formula formula;
  public final int hashCode;

  public DPDErrorValue(Derivation deriv, Rule rule, Value child1, Value child2) {
    if (child1 == null && child2 != null)
      throw new RuntimeException("Cannot have child1 == null while child2 == " + child2);
    this.rule = rule;
    this.child1 = child1;
    this.child2 = child2;
    if (child1 == null && child2 == null)
      // No children: Use the formula instead of the children
      formula = deriv.formula;
    else
      formula = null;
    this.hashCode = rule.hashCode()
        + (child1 == null ? 0 : child1.hashCode()) * 359
        + (child2 == null ? 0 : child2.hashCode()) * 438
        + (formula == null ? 0 : formula.hashCode()) * 502;
  }

  public DPDErrorValue(Derivation deriv, Rule rule, Derivation child1, Derivation child2) {
    this(deriv, rule, child1 == null ? null : child1.value, child2 == null ? null : child2.value);
  }

  @Override
  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("dperror");
    tree.addChild(rule.toLispTree());
    if (child1 != null)
      tree.addChild(child1.toLispTree());
    if (child2 != null)
      tree.addChild(child2.toLispTree());
    if (child1 == null && child2 == null)
      tree.addChild(formula.toLispTree());
    return tree;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DPDErrorValue that = (DPDErrorValue) o;
    if (rule != that.rule) return false;
    if ((child1 == null && that.child1 != null) || (child1 != null && !child1.equals(that.child1))) return false;
    if ((child2 == null && that.child2 != null) || (child2 != null && !child2.equals(that.child2))) return false;
    if ((formula == null && that.formula != null) || (formula != null && !formula.equals(that.formula))) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

}
