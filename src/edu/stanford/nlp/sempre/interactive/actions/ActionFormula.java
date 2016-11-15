package edu.stanford.nlp.sempre.interactive.actions;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Formulas;
import fig.basic.LispTree;
import fig.basic.LogInfo;

import java.util.List;

/**
 * Formula represents actions and their combinations. : is used as a prefix to denote an ActionFormula
 * primitive (: actioname args)
 * sequential (:s ActionFormula ActionFormula ...)
 * repeat (:rep Number ActionFormula)
 * conditional (:if Set ActionFormula)
 * scope (:scope Set ActionFormula)
 * @author Sida Wang
 */
public class ActionFormula extends Formula {
  public enum Mode {
    primitive(":"), // (: remove *)
    sequential(":s"), // (:s (: add red top) (: remove this))
    repeat(":loop"), // (:loop (count (has color green)) (: add red top))
    conditional(":if"), // (:if (count (has color green)) (: add red top))
    forset(":for"), // (:for (and this (color red)) (:s (: add red top) (: add yellow top) (: remove)))
    foreach(":foreach"), // (:foreach * (add ((reverse color) this) top))
    assignment(":let"), // (:let * @x), not implemented
    isolate(":isolate"),
    other(":?"); // (:scope this (blah))

    private final String value;
    Mode(String value) {this.value = value;}
    @Override
    public String toString() {
      return this.value;
    }
  };
  public final Mode mode;
  public final List<Formula> args;

  public ActionFormula(Mode mode, List<Formula> args) {
    this.mode = mode;
    this.args = args;
  }

  public static Mode parseMode(String mode) {
    if (mode == null) return null;
    for (Mode m : Mode.values()) {
      // LogInfo.logs("mode string %s \t== %s \t!= %s", m.toString(), mode, m.name());
      if (m.toString().equals(mode))
        return m;
    }
    if (mode.startsWith(":"))
      throw new RuntimeException("Unsupported ActionFormula mode");
    return null;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild(this.mode.toString());
    for (Formula arg : args)
      tree.addChild(arg.toLispTree());
    return tree;
  }

  @Override
  public void forEach(Function<Formula, Boolean> func) {
    if (!func.apply(this)) {
      for (Formula arg: args)
        arg.forEach(func);
    }
  }

  @Override
  public Formula map(Function<Formula, Formula> transform) {
    Formula result = transform.apply(this);
    if (result != null) return result;
    List<Formula> newArgs = Lists.newArrayList();
    for (Formula arg : args)
      newArgs.add(arg.map(transform));
    return new ActionFormula(this.mode, newArgs);
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> transform, boolean alwaysRecurse) {
    List<Formula> res = transform.apply(this);
    if (res.isEmpty() || alwaysRecurse) {
      for (Formula arg : args)
        res.addAll(arg.mapToList(transform, alwaysRecurse));
    }
    return res;
  }

  @SuppressWarnings({"equalshashcode"})
  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof ActionFormula)) return false;
    ActionFormula that = (ActionFormula) thatObj;
    if (!this.mode.equals(that.mode)) return false;
    if (!this.args.equals(that.args)) return false;
    return true;
  }

  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + mode.hashCode();
    hash = hash * 0xd3a2646c + args.hashCode();
    return hash;
  }
}
