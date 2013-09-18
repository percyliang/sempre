package edu.stanford.nlp.sempre;

import fig.basic.LispTree;

import java.util.ArrayList;
import java.util.List;

public class ListValue extends Value {
  public final List<Value> values;

  public ListValue(LispTree tree) {
    values = new ArrayList<Value>();
    for (int i = 1; i < tree.children.size(); i++)
      values.add(Values.fromLispTree(tree.child(i)));
  }

  public ListValue(List<Value> values) { this.values = values; }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("list");
    for (Value value : values)
      tree.addChild(value.toLispTree());
    return tree;
  }

  // Compute F1 score between two lists (partial match).
  // this is target, that is predicted.
  public double getCompatibility(Value thatValue) {
    if (!(thatValue instanceof ListValue))
      return 0;
    ListValue that = (ListValue) thatValue;

    if (this.values.size() == 0 && that.values.size() == 0)
      return 1;
    if (this.values.size() == 0 || that.values.size() == 0)
      return 0;

    double precision = 0;
    for (Value v2 : that.values) {  // For every predicted value...
      double score = 0;
      for (Value v1 : this.values)
        score = Math.max(score, v1.getCompatibility(v2));
      precision += score;
    }
    precision /= that.values.size();
    assert precision >= 0 && precision <= 1 : precision;

    double recall = 0;
    for (Value v1 : this.values) {  // For every true value...
      double score = 0;
      for (Value v2 : that.values)
        score = Math.max(score, v1.getCompatibility(v2));
      recall += score;
    }
    recall /= this.values.size();
    assert recall >= 0 && recall <= 1 : recall;

    if (precision + recall == 0) return 0;

    double f1 = 2 * precision * recall / (precision + recall);
    assert f1 >= 0 && f1 <= 1 : f1;

    return f1;
  }

  public void log() {
    for (Value value : values)
      value.log();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListValue that = (ListValue) o;
    if (!values.equals(that.values)) return false;
    return true;
  }

  @Override public int hashCode() { return values.hashCode(); }
}
