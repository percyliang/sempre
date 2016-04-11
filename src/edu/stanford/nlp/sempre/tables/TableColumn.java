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
  public final String fieldName;
  public final int index;
  // Property Name
  public final NameValue propertyNameValue;
  // Property Type (FuncSemType)
  public final SemType propertySemType;
  // Children Cell's Type (EntitySemType)
  public final String cellTypeString;
  public final NameValue cellTypeValue;
  public final SemType cellSemType;

  public TableColumn(String originalString, String fieldName, int index) {
    this.children = new ArrayList<>();
    this.originalString = originalString;
    this.fieldName = fieldName;
    this.index = index;
    this.propertyNameValue = new NameValue(TableTypeSystem.getPropertyName(fieldName), originalString);
    this.propertySemType = TableTypeSystem.getPropertySemType(fieldName);
    this.cellTypeString = TableTypeSystem.getCellType(fieldName);
    this.cellTypeValue = new NameValue(this.cellTypeString, originalString);
    this.cellSemType = SemType.newAtomicSemType(this.cellTypeString);
  }

  public static Set<String> getReservedFieldNames() {
    Set<String> usedNames = new HashSet<>();
    usedNames.add("next");
    usedNames.add("index");
    return usedNames;
  }

  @Override
  public String toString() {
    return propertyNameValue.toString();
  }
}