package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

/**
 * Represents the description part of a NameValue ("Barack Obama" rather than
 * the id fb:en.barack_obama).
 *
 * @author Andrew Chou
 */
public class DescriptionValue extends Value {
  public final String value;

  public DescriptionValue(LispTree tree) { this(tree.child(1).value); }
  public DescriptionValue(String value) { this.value = value; }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("description");
    tree.addChild(value);
    return tree;
  }

  @Override public int hashCode() { return value.hashCode(); }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DescriptionValue that = (DescriptionValue) o;
    return this.value.equals(that.value);
  }
}
