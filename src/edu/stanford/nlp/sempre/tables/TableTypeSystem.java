package edu.stanford.nlp.sempre.tables;

import java.util.*;
import java.util.function.Function;

import edu.stanford.nlp.sempre.*;

/**
 * Typing System for table. Affects naming convention and how the types of formulas are inferred.
 *
 *    ROW: name = fb:row.r[index]        | type = fb:type.row
 *   CELL: name = fb:cell.[string]       | type = fb:type.cell
 * COLUMN: name = fb:row.row.[fieldName] | type = (-> fb:type.cell fb:type.row)
 *   PART: name = fb:part.[string]       | type = fb:type.part
 *
 * Identical strings in different cells are mapped to the same name.
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
  public static final String CELL_TYPE = "fb:type.cell";
  public static final SemType CELL_SEMTYPE = SemType.newAtomicSemType(CELL_TYPE);
  public static final String PART_TYPE = "fb:type.part";
  public static final SemType PART_SEMTYPE = SemType.newAtomicSemType(PART_TYPE);

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
  public static final NameValue CELL_STR1_VALUE = new NameValue("fb:cell.cell.str1");
  public static final NameValue CELL_STR2_VALUE = new NameValue("fb:cell.cell.str2");
  public static final NameValue CELL_PART_VALUE = new NameValue("fb:cell.cell.part");
  public static final Map<Value, SemType> CELL_PROPERTIES = new HashMap<>();
  static {
    CELL_PROPERTIES.put(CELL_NUMBER_VALUE, SemType.newFuncSemType(CanonicalNames.NUMBER, CELL_TYPE));
    CELL_PROPERTIES.put(CELL_DATE_VALUE, SemType.newFuncSemType(CanonicalNames.DATE, CELL_TYPE));
    CELL_PROPERTIES.put(CELL_NUM2_VALUE, SemType.newFuncSemType(CanonicalNames.NUMBER, CELL_TYPE));
    CELL_PROPERTIES.put(CELL_STR1_VALUE, SemType.newFuncSemType(PART_TYPE, CELL_TYPE));
    CELL_PROPERTIES.put(CELL_STR2_VALUE, SemType.newFuncSemType(PART_TYPE, CELL_TYPE));
    CELL_PROPERTIES.put(CELL_PART_VALUE, SemType.newFuncSemType(PART_TYPE, CELL_TYPE));
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
   * Look up the (normalized) "original string" in originalStringToId.
   * If found, return the found id (for creating a NameValue).
   * If not, create a new id, add it to originalStringToId, and return the id.
   *
   * CanonicalNameToId should map the canonical name (e.g., "palo_alto")
   * to the actual id (e.g., "fb:cell.palo_alto")
   */
  public static String getOrCreateName(String originalString, Map<String, String> originalStringToId,
      Function<String, String> canonicalNameToId) {
    String normalized = StringNormalizationUtils.characterNormalize(originalString).toLowerCase();
    String id = originalStringToId.get(normalized);
    if (id == null) {
      String canonicalName = TableTypeSystem.canonicalizeName(normalized);
      id = TableTypeSystem.getUnusedName(
          canonicalNameToId.apply(canonicalName), originalStringToId.values());
      originalStringToId.put(normalized, id);
    }
    return id;
  }

  /**
   * When id = {prefix}{string without .}.{name}, get {name}
   * (Note that {prefix} can contain ".")
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

  public static String getCellName(String id) {
    return CELL_NAME_PREFIX + "." + id;
  }

  public static String getPartName(String id) {
    return PART_NAME_PREFIX + "." + id;
  }

  public static String getRowPropertyName(String fieldName) {
    return ROW_PROPERTY_NAME_PREFIX + "." + fieldName;
  }

  public static String getRowConsecutivePropertyName(String fieldName) {
    return ROW_CONSECUTIVE_PROPERTY_NAME_PREFIX + "." + fieldName;
  }

  public static SemType getEntityTypeFromId(String id) {
    if (id.startsWith(ROW_NAME_PREFIX)) return ROW_SEMTYPE;
    if (id.startsWith(CELL_NAME_PREFIX)) return CELL_SEMTYPE;
    if (id.startsWith(PART_NAME_PREFIX)) return PART_SEMTYPE;
    return null;
  }

  public static SemType getPropertyTypeFromId(String id) {
    NameValue value = new NameValue(id);
    // Predefined properties
    SemType type = ROW_RELATIONS.get(value);
    if (type != null) return type;
    type = CELL_PROPERTIES.get(value);
    if (type != null) return type;
    // Column-based properties
    if (id.startsWith(ROW_PROPERTY_NAME_PREFIX))
      return SemType.newFuncSemType(CELL_TYPE, ROW_TYPE);
    if (id.startsWith(ROW_CONSECUTIVE_PROPERTY_NAME_PREFIX))
      return SemType.newFuncSemType(CanonicalNames.NUMBER, ROW_TYPE);
    return null;
  }

}
