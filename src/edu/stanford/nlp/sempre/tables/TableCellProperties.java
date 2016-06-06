package edu.stanford.nlp.sempre.tables;

import java.util.*;

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
  public final Set<TableColumn> columns;

  public TableCellProperties(String id, String originalString) {
    this.id = id;
    this.originalString = originalString;
    this.nameValue = new NameValue(id, originalString);
    this.metadata = ArrayListMultimap.create();
    this.columns = new HashSet<>();
  }

  /** Create a copy without the columns field. */
  public TableCellProperties(TableCellProperties old) {
    this.id = old.id;
    this.originalString = old.originalString;
    this.nameValue = old.nameValue;
    this.metadata = ArrayListMultimap.create(old.metadata);
    this.columns = new HashSet<>();
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