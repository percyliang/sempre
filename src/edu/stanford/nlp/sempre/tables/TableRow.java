package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;

/**
 * Represents a table row.
 *
 * In the knowledge graph, a table row is a nameless node.
 * The final denotation cannot be row nodes.
 *
 * @author ppasupat
 */
public class TableRow {
  public final List<TableCell> children;
  public final int index;
  public final NumberValue indexValue;
  public final NameValue nameValue;

  public TableRow(int index) {
    this.children = new ArrayList<>();
    this.index = index;
    this.indexValue = new NumberValue(index);
    this.nameValue = new NameValue(TableTypeSystem.getRowName(index), "" + index);
  }

  @Override
  public String toString() {
    return nameValue.toString();
  }
}