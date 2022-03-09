package edu.stanford.nlp.sempre.tables;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import edu.stanford.nlp.sempre.*;

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
  public final NameValue nameValue;
  public final Multimap<Value, Value> metadata;

  public TableCellProperties(String id, String originalString) {
    this.id = id;
    this.originalString = originalString;
    this.nameValue = new NameValue(id, originalString);
    this.metadata = ArrayListMultimap.create();
  }

  /** Create a copy without the columns field. */
  public TableCellProperties(TableCellProperties old) {
    this.id = old.id;
    this.originalString = old.originalString;
    this.nameValue = old.nameValue;
    this.metadata = ArrayListMultimap.create(old.metadata);
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