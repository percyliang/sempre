package edu.stanford.nlp.sempre.tables;

import java.util.*;

import edu.stanford.nlp.sempre.*;

/**
 * Typing System for table. Affects naming convention and how the types of formulas are inferred.
 *
 *      ROW: name = fb:row.r[index]              | type = fb:type.row
 *     CELL: name = fb:cell_[fieldName].[string] | type = (union fb:type.cell fb:column.[fieldName])
 * PROPERTY: name = fb:row.row.[fieldName]       | type = (-> (union fb:type.cell fb:column.[fieldName]) fb:type.row)
 *
 * Note that the same string in different columns are mapped to different names.
 *
 * @author ppasupat
 */
public abstract class TableTypeSystem {

  // Value names
  public static final String ROW_NAME_PREFIX = "fb:row";
  public static final String CELL_NAME_PREFIX = "fb:cell";
  public static final String PART_NAME_PREFIX = "fb:part";

  // Type names
  public static final String ROW_TYPE = "fb:type.row";
  public static final SemType ROW_SEMTYPE = SemType.newAtomicSemType(ROW_TYPE);
  public static final String CELL_GENERIC_TYPE = "fb:type.cell";
  public static final SemType CELL_GENERIC_SEMTYPE = SemType.newAtomicSemType(CELL_GENERIC_TYPE);
  public static final String CELL_SPECIFIC_TYPE_PREFIX = "fb:column";
  public static final String PART_GENERIC_TYPE = "fb:type.part";
  public static final SemType PART_GENERIC_SEMTYPE = SemType.newAtomicSemType(PART_GENERIC_TYPE);
  public static final String PART_SPECIFIC_TYPE_PREFIX = "fb:part";

  // Row relations
  public static final String ROW_PROPERTY_NAME_PREFIX = "fb:row.row";
  public static final NameValue ROW_NEXT_VALUE = new NameValue("fb:row.row.next");
  public static final NameValue ROW_INDEX_VALUE = new NameValue("fb:row.row.index");
  public static final Map<Value, SemType> ROW_RELATIONS = new HashMap<>();
  static {
    ROW_RELATIONS.put(ROW_NEXT_VALUE, SemType.newFuncSemType(ROW_TYPE, ROW_TYPE));
    ROW_RELATIONS.put(ROW_INDEX_VALUE, SemType.newFuncSemType(CanonicalNames.INT, ROW_TYPE));
  }

  public static final String ROW_CONSECUTIVE_PROPERTY_NAME_PREFIX = "fb:row.consecutive";

  // Cell properties
  public static final String CELL_PROPERTY_NAME_PREFIX = "fb:cell.cell";
  public static final NameValue CELL_NUMBER_VALUE = new NameValue("fb:cell.cell.number");
  public static final NameValue CELL_DATE_VALUE = new NameValue("fb:cell.cell.date");
  public static final NameValue CELL_NUM2_VALUE = new NameValue("fb:cell.cell.num2");
  public static final NameValue CELL_PART_VALUE = new NameValue("fb:cell.cell.part");
  public static final Map<Value, SemType> CELL_PROPERTIES = new HashMap<>();
  static {
    CELL_PROPERTIES.put(CELL_NUMBER_VALUE, SemType.newFuncSemType(CanonicalNames.NUMBER, CELL_GENERIC_TYPE));
    CELL_PROPERTIES.put(CELL_DATE_VALUE, SemType.newFuncSemType(CanonicalNames.DATE, CELL_GENERIC_TYPE));
    CELL_PROPERTIES.put(CELL_NUM2_VALUE, SemType.newFuncSemType(CanonicalNames.NUMBER, CELL_GENERIC_TYPE));
    CELL_PROPERTIES.put(CELL_PART_VALUE, SemType.newFuncSemType(PART_GENERIC_TYPE, CELL_GENERIC_TYPE));
  }

  // ============================================================
  // Helper Functions
  // ============================================================

  /**
   * Convert string entry to an alpha-numeric name
   */
  public static String canonicalizeName(String originalString) {
    String id = originalString;
    id = id.replaceAll("[^\\w]", "_");  // Replace abnormal characters with _
    id = id.replaceAll("_+", "_");      // Merge consecutive _'s
    id = id.replaceAll("_$", "");
    id = id.toLowerCase();
    if (id.length() == 0) id = "null";
    return id;
  }

  /**
   * Add suffix to make the name unique. (Does not modify usedNames)
   */
  public static String getUnusedName(String baseName, Collection<String> usedNames, String sep) {
    int suffix = 2;
    String appendedId = baseName;
    while (usedNames.contains(appendedId)) {
      appendedId = baseName + sep + (suffix++);
    }
    return appendedId;
  }
  public static String getUnusedName(String baseName, Collection<String> usedNames) {
    return getUnusedName(baseName, usedNames, "_");
  }

  /**
   * When id = [prefix]_[1].[2], get [1]. For example:
   * - fb:row_[tableId].r[index] --> [tableId]
   * - fb:cell_[fieldName].[string] --> [fieldName]
   */
  public static String getIdAfterUnderscore(String id, String prefix) {
    return id.substring(prefix.length() + 1).split("\\.", 2)[0];
  }

  /**
   * When id = [prefix]_[1].[2], get [2]. For example:
   * - fb:row_[tableId].r[index] --> r[index]
   * - fb:cell.[string] or fb:cell_[fieldName].[string] --> [string]
   */
  public static String getIdAfterPeriod(String id, String prefix) {
    return id.substring(prefix.length()).split("\\.", 2)[1];
  }

  public static boolean isRowProperty(Value r) {
    return r instanceof NameValue && ((NameValue) r).id.startsWith(ROW_PROPERTY_NAME_PREFIX);
  }

  public static boolean isRowConsecutiveProperty(Value r) {
    return r instanceof NameValue && ((NameValue) r).id.startsWith(ROW_CONSECUTIVE_PROPERTY_NAME_PREFIX);
  }

  public static boolean isCellProperty(Value r) {
    return r instanceof NameValue && ((NameValue) r).id.startsWith(CELL_PROPERTY_NAME_PREFIX);
  }

  // ============================================================
  // Main Functions
  // ============================================================

  public static String getRowName(int index) {
    return ROW_NAME_PREFIX + ".r" + index;
  }

  public static String getCellName(String id, String fieldName) {
    return CELL_NAME_PREFIX + "_" + fieldName + "." + id;
  }
  
  public static String getPartName(String id, String fieldName) {
    return PART_NAME_PREFIX + "_" + fieldName + "." + id;
  }

  public static String getCellType(String fieldName) {
    return CELL_SPECIFIC_TYPE_PREFIX + "." + fieldName;
  }

  public static String getPartType(String fieldName) {
    return PART_SPECIFIC_TYPE_PREFIX + "." + fieldName;
  }

  public static String getRowPropertyName(String fieldName) {
    return ROW_PROPERTY_NAME_PREFIX + "." + fieldName;
  }

  public static String getRowConsecutivePropertyName(String fieldName) {
    return ROW_CONSECUTIVE_PROPERTY_NAME_PREFIX + "." + fieldName;
  }

  public static SemType getEntityTypeFromId(String entity) {
    if (entity.startsWith(CELL_NAME_PREFIX)) {
      String fieldName = getIdAfterUnderscore(entity, CELL_NAME_PREFIX);
      return SemType.newUnionSemType(CELL_GENERIC_TYPE, getCellType(fieldName));
    } else if (entity.startsWith(PART_NAME_PREFIX)) {
      String fieldName = getIdAfterUnderscore(entity, PART_NAME_PREFIX);
      return SemType.newUnionSemType(PART_GENERIC_TYPE, getPartType(fieldName));
    }
    return null;
  }

  public static SemType getPropertyTypeFromId(String property) {
    if (property.startsWith(ROW_PROPERTY_NAME_PREFIX)) {
      SemType rowPropertyType = ROW_RELATIONS.get(new NameValue(property));
      if (rowPropertyType != null) return rowPropertyType;
      String fieldName = getIdAfterPeriod(property, ROW_PROPERTY_NAME_PREFIX);
      return new FuncSemType(SemType.newUnionSemType(getCellType(fieldName), CELL_GENERIC_TYPE), ROW_SEMTYPE);
    }
    if (property.startsWith(CELL_PROPERTY_NAME_PREFIX)) {
      SemType cellPropertyType = CELL_PROPERTIES.get(new NameValue(property));
      return cellPropertyType;
    }
    return null;
  }

  public static String getPropertyOfEntity(String entity) {
    if (entity.startsWith(CELL_NAME_PREFIX)) {
      String fieldName = getIdAfterUnderscore(entity, CELL_NAME_PREFIX);
      return getRowPropertyName(fieldName);
    }
    return null;
  }

}
