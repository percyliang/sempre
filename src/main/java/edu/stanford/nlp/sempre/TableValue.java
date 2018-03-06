package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.StrUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a table (has a header and a list of rows).
 *
 *   (table (State Capital) ((name fb:en.california) (name fb:en.sacramento)) ((name fb:en.oregon) (name fb:en.salem)))
 *
 * Future: contain information about which columns are important (the head of a
 * phrase)?
 *
 * @author Percy Liang
 */
public class TableValue extends Value {
  public final List<String> header;
  public final List<List<Value>> rows;

  public int numRows() { return rows.size(); }
  public int numCols() { return header.size(); }

  public TableValue(LispTree tree) {
    header = new ArrayList<String>();
    rows = new ArrayList<List<Value>>();
    // Read header
    LispTree headerTree = tree.child(1);
    for (LispTree item : headerTree.children)
      header.add(item.value);
    // Read rows
    for (int i = 2; i < tree.children.size(); i++) {
      List<Value> row = new ArrayList<Value>();
      for (LispTree item : tree.child(i).children)
        row.add(Values.fromLispTree(item));
      rows.add(row);
    }
  }

  public TableValue(List<String> header, List<List<Value>> rows) {
    this.header = header;
    this.rows = rows;
  }

  public LispTree toLispTree() {
    LispTree tree = LispTree.proto.newList();
    tree.addChild("table");
    LispTree headerTree = LispTree.proto.newList();
    for (String item : header)
      headerTree.addChild(item);
    tree.addChild(headerTree);
    for (List<Value> row : rows) {
      LispTree rowTree = LispTree.proto.newList();
      for (Value value : row)
        rowTree.addChild(value == null ? LispTree.proto.newLeaf(null) : value.toLispTree());
      tree.addChild(rowTree);
    }
    return tree;
  }

  public void log() {
    LogInfo.begin_track("%s", StrUtils.join(header, "\t"));
    for (List<Value> row : rows)
      LogInfo.logs("%s", StrUtils.join(row, "\t"));
    LogInfo.end_track();
  }

  // Note: don't compare the headers right now
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TableValue that = (TableValue) o;
    if (!rows.equals(that.rows)) return false;
    return true;
  }

  @Override public int hashCode() { return rows.hashCode(); }
}
