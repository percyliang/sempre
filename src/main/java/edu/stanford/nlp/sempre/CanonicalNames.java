package edu.stanford.nlp.sempre;

import java.util.*;

/**
 * List of canonical names that we borrowed from Freebase.
 *
 * These names and helper methods are independent from the Freebase schema
 * (even though the names begin with "fb:").
 *
 * @author ppasupat
 */
public final class CanonicalNames {
  private CanonicalNames() { }

  // Standard type names
  public static final String PREFIX = "fb:";
  public static final String BOOLEAN = "fb:type.boolean";
  public static final String INT = "fb:type.int";
  public static final String FLOAT = "fb:type.float";
  public static final String DATE = "fb:type.datetime";
  public static final String TIME = "fb:type.time";
  public static final String TEXT = "fb:type.text";
  public static final String NUMBER = "fb:type.number";
  public static final String ENTITY = "fb:common.topic";
  public static final String ANY = "fb:type.any";

  public static final List<String> PRIMITIVES = Collections.unmodifiableList(
      Arrays.asList(BOOLEAN, INT, FLOAT, DATE, TEXT, NUMBER));

  // Standard relations
  public static final String TYPE = "fb:type.object.type";
  public static final String NAME = "fb:type.object.name";

  // Special Unary: star (*)
  public static final String STAR = "*";

  // Special Binaries: comparison
  public static final Map<String, String> COMPARATOR_REVERSE = new HashMap<>();
  static {
    COMPARATOR_REVERSE.put("!=", "!=");     // a != b implies b != a
    COMPARATOR_REVERSE.put("<", ">=");
    COMPARATOR_REVERSE.put(">", "<=");
    COMPARATOR_REVERSE.put("<=", ">");
    COMPARATOR_REVERSE.put(">=", "<");
  }
  public static final Set<String> COMPARATORS = COMPARATOR_REVERSE.keySet();

  // Special Binary: colon (:)
  public static final String COLON = ":";

  // SemType for special unaries and binaries
  public static final Map<String, SemType> SPECIAL_SEMTYPES = new HashMap<>();
  static {
    SPECIAL_SEMTYPES.put("*", SemType.anyType);
    SPECIAL_SEMTYPES.put("!=", SemType.anyAnyFunc);
    SPECIAL_SEMTYPES.put("<", SemType.compareFunc);
    SPECIAL_SEMTYPES.put(">", SemType.compareFunc);
    SPECIAL_SEMTYPES.put("<=", SemType.compareFunc);
    SPECIAL_SEMTYPES.put(">=", SemType.compareFunc);
    SPECIAL_SEMTYPES.put(":", SemType.anyAnyFunc);
  }

  // Unary: fb:domain.type [contains exactly one period]
  // Special Unary: star (*)
  public static boolean isUnary(String s) {
    if (STAR.equals(s)) return true;
    int i = s.indexOf('.');
    if (i == -1) return false;
    i = s.indexOf('.', i + 1);
    if (i == -1) return true;
    return false;
  }
  public static boolean isUnary(Value value) {
    return value instanceof NameValue && isUnary((((NameValue) value).id));
  }

  // Binary: fb:domain.type.property [contains two periods]
  // Also catch reversed binary shorthand [!fb:people.person.parent]
  // Special Binaries: comparison (<, >, etc.) and colon (:)
  public static boolean isBinary(String s) {
    if (COMPARATORS.contains(s) || COLON.equals(s)) return true;
    int i = s.indexOf('.');
    if (i == -1) return false;
    i = s.indexOf('.', i + 1);
    if (i == -1) return false;
    return true;
  }
  public static boolean isBinary(Value value) {
    return value instanceof NameValue && isBinary((((NameValue) value).id));
  }

  // Return whether |property| is the name of a reverse property.
  // Convention: ! is the prefix for reverses.
  public static boolean isReverseProperty(String property) {
    return property.startsWith("!") && !property.equals("!=");
  }
  public static boolean isReverseProperty(Value value) {
    return value instanceof NameValue && isReverseProperty(((NameValue) value).id);
  }

  // Return the reverse property as a String
  public static String reverseProperty(String property) {
    if (COMPARATORS.contains(property))
      return COMPARATOR_REVERSE.get(property);
    if (isReverseProperty(property))
      return property.substring(1);
    else return "!" + property;
  }
  public static NameValue reverseProperty(Value value) {
    if (!(value instanceof NameValue))
      throw new RuntimeException("Cannot call reverseProperty on " + value);
    return new NameValue(reverseProperty(((NameValue) value).id));
  }


}
