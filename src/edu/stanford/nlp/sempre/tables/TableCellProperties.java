package edu.stanford.nlp.sempre.tables;

import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.sempre.NameValue;
import edu.stanford.nlp.sempre.Value;

/**
 * Store various properties of a cell.
 *
 * Contract: There is only one TableCellProperties for each unique id.
 *
 * @author ppasupat
 */
public class TableCellProperties {
  public final String id;
  public final String originalString;
  public final NameValue entityNameValue;
  public final Map<Value, Value> metadata;

  public TableCellProperties(String id, String originalString) {
    this.id = id;
    this.originalString = originalString;
    this.entityNameValue = new NameValue(id, originalString);
    this.metadata = new HashMap<>();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TableCellProperties)) return false;
    return id.equals(((TableCellProperties) o).id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}