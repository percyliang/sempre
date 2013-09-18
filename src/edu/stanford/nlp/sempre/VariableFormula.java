package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

/**
 * Corresponds to a variable reference.
 *
 * @author Percy Liang
 */
class VariableFormula extends PrimitiveFormula {
  public final String name;  // Name of variable.
  public VariableFormula(String name) { this.name = name; }
  public LispTree toLispTree() { return LispTree.proto.newList("var", name); }

  @Override
  public boolean equals(Object thatObj) {
    if (!(thatObj instanceof VariableFormula)) return false;
    VariableFormula that = (VariableFormula) thatObj;
    return this.name.equals(that.name);
  }
  @Override
  public int hashCode() { return name.hashCode(); }
}
