package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

public class UriValue extends Value {
  public final String value;

  public UriValue(LispTree tree) {
    this.value = tree.child(1).value;
  }

  public UriValue(String value) {
    this.value = value;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("url");
    tree.addChild(value != null ? value : "");
    return tree;
  }
  public double getCompatibility(Value thatValue) {
    if (!(thatValue instanceof UriValue))
      return 0.0;
    UriValue that = (UriValue) thatValue;
    return this.value.equals(that.value) ? 1.0 : 0.0;
  }

  @Override public int hashCode() { return value.hashCode(); }
  @Override public boolean equals(Object thatObj) {
    UriValue that = (UriValue)thatObj;
    return this.value.equals(that.value);
  }
}
