package edu.stanford.nlp.sempre;

import com.google.common.base.Function;
import fig.basic.LispTree;

/**
 * Lambda abstraction (lambda |var| |body|)
 * Percy Liang
 */
public class LambdaFormula extends Formula {
  public final String var;
  public final Formula body;

  public LambdaFormula(String var, Formula body) {
    this.var = var;
    this.body = body;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("lambda");
    tree.addChild(var);
    tree.addChild(body.toLispTree());
    return tree;
  }

  public Formula map(Function<Formula, Formula> func) {
    Formula result = func.apply(this);
    return result == null ? new LambdaFormula(var, body.map(func)) : result;
  }

  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof LambdaFormula)) return false;
    LambdaFormula that = (LambdaFormula) thatObj;
    return this.var.equals(that.var) && this.body.equals(that.body);
  }
  
  public int computeHashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + var.hashCode();
    hash = hash * 0xd3a2646c + body.hashCode();
    return hash;
  }
}
