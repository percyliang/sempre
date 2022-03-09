package edu.stanford.nlp.sempre;

import java.util.*;

import fig.basic.LispTree;
import fig.basic.LogInfo;

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
      tree.addChild(value == null ? LispTree.proto.newLeaf(null) : value.toLispTree());
    return tree;
  }

  public void log() {
    for (Value value : values)
      LogInfo.logs("%s", value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListValue that = (ListValue) o;
    return values.equals(that.values);
  }

  @Override public int hashCode() { return values.hashCode(); }

  // Sorted on string representation
  public ListValue getSorted() {
    List<Value> sorted = new ArrayList<>(values);
    Collections.sort(sorted,
        (Value v1, Value v2) -> (
            v1 == null ? "null" : v1.sortString()).compareTo(v2 == null ? "null" : v2.sortString()));
    return new ListValue(sorted);
  }

  // Unique
  public ListValue getUnique() {
    List<Value> sorted = new ArrayList<>(new HashSet<>(values));
    Collections.sort(sorted,
        (Value v1, Value v2) -> (
            v1 == null ? "null" : v1.sortString()).compareTo(v2 == null ? "null" : v2.sortString()));
    return new ListValue(sorted);
  }
}
