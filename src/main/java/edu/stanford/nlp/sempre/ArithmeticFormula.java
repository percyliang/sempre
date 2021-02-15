package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

import java.util.List;

/**
 * Performs arithmetic operations (+, -, *, /).
 * Note that these are non-binary relations, which means we can't model them
 * using a join.
 *
 * @author Percy Liang
 */
public class ArithmeticFormula extends Formula {
  public enum Mode { add, sub, mul, div };
  public final Mode mode;
  public final Formula child1;
  public final Formula child2;

  public ArithmeticFormula(Mode mode, Formula child1, Formula child2) {
    this.mode = mode;
    this.child1 = child1;
    this.child2 = child2;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild(modeToString(mode));
    tree.addChild(child1.toLispTree());
    tree.addChild(child2.toLispTree());
    return tree;
  }

  @Override
  public void forEach(Function<Formula, Boolean> func) {
    if (!func.apply(this)) { child1.forEach(func); child2.forEach(func); }
  }

  @Override
  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new ArithmeticFormula(mode, child1.map(func), child2.map(func)) : result;
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> func, boolean alwaysRecurse) {
    List<Formula> res = func.apply(this);
    if (res.isEmpty() || alwaysRecurse) {
      res.addAll(child1.mapToList(func, alwaysRecurse));
      res.addAll(child2.mapToList(func, alwaysRecurse));
    }
    return res;
  }

  public static Mode parseMode(String mode) {
    if ("+".equals(mode)) return Mode.add;
    if ("-".equals(mode)) return Mode.sub;
    if ("*".equals(mode)) return Mode.mul;
    if ("/".equals(mode)) return Mode.div;
    return null;
  }

  public static String modeToString(Mode mode) {
    switch (mode) {
      case add: return "+";
      case sub: return "-";
      case mul: return "*";
      case div: return "/";
      default: throw new RuntimeException("Invalid mode: " + mode);
    }
  }

  @SuppressWarnings({"equalshashcode"})
  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof ArithmeticFormula)) return false;
    ArithmeticFormula that = (ArithmeticFormula) thatObj;
    if (this.mode != that.mode) return false;
    if (!this.child1.equals(that.child1)) return false;
    if (!this.child2.equals(that.child2)) return false;
    return true;
  }

  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + mode.toString().hashCode();  // Note: don't call hashCode() on mode directly.
    hash = hash * 0xd3a2646c + child1.hashCode();
    hash = hash * 0xd3a2646c + child2.hashCode();
    return hash;
  }
}
