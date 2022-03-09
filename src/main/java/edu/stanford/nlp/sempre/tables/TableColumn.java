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
  // Relation Name
  public final NameValue relationNameValue, relationConsecutiveNameValue;
  // Children Cell's Type (EntitySemType)
  public final String cellTypeString;
  public final NameValue cellTypeValue;
  public final SemType cellSemType;

  public TableColumn(String originalString, String columnName, int index) {
    this.children = new ArrayList<>();
    this.originalString = originalString;
    this.columnName = columnName;
    this.index = index;
    this.relationNameValue = new NameValue(TableTypeSystem.getRowPropertyName(columnName), originalString);
    this.relationConsecutiveNameValue = new NameValue(TableTypeSystem.getRowConsecutivePropertyName(columnName), originalString);
    this.cellTypeString = TableTypeSystem.getCellType(columnName);
    this.cellTypeValue = new NameValue(this.cellTypeString, originalString);
    this.cellSemType = SemType.newAtomicSemType(this.cellTypeString);
  }

  /** Create a copy without the children field. */
  public TableColumn(TableColumn old) {
    this.children = new ArrayList<>();
    this.originalString = old.originalString;
    this.columnName = old.columnName;
    this.index = old.index;
    this.relationNameValue = old.relationNameValue;
    this.relationConsecutiveNameValue = old.relationConsecutiveNameValue;
    this.cellTypeString = old.cellTypeString;
    this.cellTypeValue = old.cellTypeValue;
    this.cellSemType = old.cellSemType;
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