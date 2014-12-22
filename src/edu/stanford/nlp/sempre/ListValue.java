package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.LogInfo;

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
    if (!values.equals(that.values)) return false;
    return true;
  }

  @Override public int hashCode() { return values.hashCode(); }
}
