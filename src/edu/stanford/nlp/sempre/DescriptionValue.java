package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.Option;

/**
 * Represents the description part of a NameValue ("Barack Obama" rather than
 * the id fb:en.barack_obama).
 *
 * @author Andrew Chou
 */
public class DescriptionValue extends Value {
  public static class Options {
    @Option(gloss = "Verbose.") public boolean verbose = false;
  }
  public static Options opts = new Options();

  public final String value;

  public DescriptionValue(LispTree tree) { this(tree.child(1).value); }
  public DescriptionValue(String value) { this.value = value; }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("description");
    tree.addChild(value);
    return tree;
  }

  public double getCompatibility(Value thatValue) {
    // Match the description part of NameValue.
    if (thatValue instanceof NameValue)
      return value.equals(((NameValue)thatValue).description) ? 1 : 0;

    return super.getCompatibility(thatValue);
  }

  @Override public int hashCode() { return value.hashCode(); }
  @Override public boolean equals(Object thatObj) {
    if (!(thatObj instanceof DescriptionValue)) return false;
    DescriptionValue that = (DescriptionValue)thatObj;
    return this.value.equals(that.value);
  }
}
