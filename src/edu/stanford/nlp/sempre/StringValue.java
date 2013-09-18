package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

/**
 * Represents a string value.
 * @author Percy Liang
 **/
public class StringValue extends Value {
  public final String value;

  public StringValue(String value) { this.value = value; }
  public StringValue(LispTree tree) { this.value = tree.child(1).value; }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("string");
    tree.addChild(value);
    return tree;
  }

  public double getCompatibility(Value thatValue) {
    if (!(thatValue instanceof StringValue))
      return 0;
    StringValue that = (StringValue) thatValue;
    return this.value.equals(that.value) ? 1 : 0;
  }

  @Override public int hashCode() { return value.hashCode(); }
  @Override public boolean equals(Object thatObj) {
    if (!(thatObj instanceof StringValue)) return false;
    StringValue that = (StringValue)thatObj;
    return this.value.equals(that.value);
  }
}
