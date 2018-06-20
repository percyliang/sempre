package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.Fmt;

/**
 * Represents a numerical value (optionally comes with a unit).
 * In the future, might want to split this into an Integer version?
 *
 * @author Percy Liang
 */
public class NumberValue extends Value {
  public static final String unitless = "fb:en.unitless";
  public static final String yearUnit = "fb:en.year";

  public final double value;
  public final String unit;  // What measurement (e.g., "fb:en.meter" or unitless)

  public NumberValue(double value) {
    this(value, unitless);
  }

  public NumberValue(double value, String unit) {
    this.value = value;
    this.unit = unit;
  }

  public NumberValue(LispTree tree) {
    this.value = Double.parseDouble(tree.child(1).value);
    this.unit = 2 < tree.children.size() ? tree.child(2).value : unitless;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("number");
    tree.addChild(Fmt.D(value));
    if (!unit.equals(unitless))
      tree.addChild(unit);
    return tree;
  }

  @Override public String sortString() { return "" + value; }
  @Override public String pureString() { return "" + value; }

  @Override public int hashCode() { return Double.valueOf(value).hashCode(); }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NumberValue that = (NumberValue) o;
    if (this.value != that.value) return false;  // Warning: doing exact equality checking
    if (!this.unit.equals(that.unit)) return false;
    return true;
  }
}
