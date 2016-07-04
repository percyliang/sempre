package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;

/**
 * Represents a table column.
 *
 * The column header is used as the relation name.
 *
 * @author ppasupat
 */
public class TableColumn {
  public final List<TableCell> children;
  public final String originalString;
  public final String columnName;
  public final int index;
  public final NameValue relationNameValue, relationConsecutiveNameValue;

  public TableColumn(String originalString, String columnName, int index) {
    this.children = new ArrayList<>();
    this.originalString = originalString;
    this.columnName = columnName;
    this.index = index;
    this.relationNameValue = new NameValue(TableTypeSystem.getRowPropertyName(columnName), originalString);
    this.relationConsecutiveNameValue = new NameValue(TableTypeSystem.getRowConsecutivePropertyName(columnName), originalString);
  }

  /** Create a copy without the children field. */
  public TableColumn(TableColumn old) {
    this.children = new ArrayList<>();
    this.originalString = old.originalString;
    this.columnName = old.columnName;
    this.index = old.index;
    this.relationNameValue = old.relationNameValue;
    this.relationConsecutiveNameValue = old.relationConsecutiveNameValue;
  }

  public static Set<String> getReservedFieldNames() {
    Set<String> usedNames = new HashSet<>();
    usedNames.add("next");
    usedNames.add("index");
    return usedNames;
  }

  @Override
  public String toString() {
    return relationNameValue.toString();
  }

  public boolean hasConsecutive() {
    NameValue previousCell = null;
    for (TableCell child : children) {
      if (child.properties.nameValue.equals(previousCell)) return true;
      previousCell = child.properties.nameValue;
    }
    return false;
  }

  public Collection<Value> getAllNormalization() {
    Set<Value> normalizations = new HashSet<>();
    for (TableCell cell : children) {
      normalizations.addAll(cell.properties.metadata.keySet());
    }
    return normalizations;
  }
}