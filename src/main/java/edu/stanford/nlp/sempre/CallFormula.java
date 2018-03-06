package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import fig.basic.LispTree;

import java.util.List;

/**
 * A CallFormula represents a function call.
 * See JavaExecutor for the semantics of this formula.
 *   (call func arg_1 ... arg_k)
 *
 * @author Percy Liang
 */
public class CallFormula extends Formula {
  public final Formula func;
  public final List<Formula> args;

  public CallFormula(String func, List<Formula> args) {
    this(Formulas.newNameFormula(func), args);
  }

  public CallFormula(Formula func, List<Formula> args) {
    this.func = func;
    this.args = args;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("call");
    tree.addChild(func.toLispTree());
    for (Formula arg : args)
      tree.addChild(arg.toLispTree());
    return tree;
  }

  @Override
  public void forEach(Function<Formula, Boolean> func) {
    if (!func.apply(this)) {
      this.func.forEach(func);
      for (Formula arg: args)
        arg.forEach(func);
    }
  }

  @Override
  public Formula map(Function<Formula, Formula> transform) {
    Formula result = transform.apply(this);
    if (result != null) return result;
    Formula newFunc = func.map(transform);
    List<Formula> newArgs = Lists.newArrayList();
    for (Formula arg : args)
      newArgs.add(arg.map(transform));
    return new CallFormula(newFunc, newArgs);
  }

  @Override
  public List<Formula> mapToList(Function<Formula, List<Formula>> transform, boolean alwaysRecurse) {
    List<Formula> res = transform.apply(this);
    if (res.isEmpty() || alwaysRecurse) {
      res.addAll(func.mapToList(transform, alwaysRecurse));
      for (Formula arg : args)
        res.addAll(arg.mapToList(transform, alwaysRecurse));
    }
    return res;
  }

  @SuppressWarnings({"equalshashcode"})
  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof CallFormula)) return false;
    CallFormula that = (CallFormula) thatObj;
    if (!this.func.equals(that.func)) return false;
    if (!this.args.equals(that.args)) return false;
    return true;
  }

  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + func.hashCode();
    hash = hash * 0xd3a2646c + args.hashCode();
    return hash;
  }
}
