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

  @Override public String sortString() { return "\"" + value + "\""; }
  @Override public String pureString() { return value; }

  @Override public int hashCode() { return value.hashCode(); }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StringValue that = (StringValue) o;
    return this.value.equals(that.value);
  }
}
