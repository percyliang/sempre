package edu.stanford.nlp.sempre;

import java.util.*;

/**
 * List of canonical names that we borrowed from Freebase.
 *
 * These names and helper methods are independent from the Freebase schema.
 *
 * @author ppasupat
 */
public final class CanonicalNames {
  private CanonicalNames() { }

  // Standard type names
  public static final String BOOLEAN = "fb:type.boolean";
  public static final String INT = "fb:type.int";
  public static final String FLOAT = "fb:type.float";
  public static final String DATE = "fb:type.datetime";
  public static final String TEXT = "fb:type.text";
  public static final String NUMBER = "fb:type.number";
  public static final String ENTITY = "fb:common.topic";
  public static final String ANY = "fb:type.any";

  public static final List<String> PRIMITIVES = Collections.unmodifiableList(
      Arrays.asList(BOOLEAN, INT, FLOAT, DATE, TEXT, NUMBER));

  // Standard relations
  public static final String TYPE = "fb:type.object.type";
  public static final String NAME = "fb:type.object.name";

  // Return whether |property| is the name of a reverse property.
  // Convention: ! is the prefix for reverses.
  public static boolean isReverseProperty(String property) {
    return property.startsWith("!") && !property.equals("!=");
  }
  public static String reverseProperty(String property) {
    if (isReverseProperty(property)) return property.substring(1);
    else return "!" + property;
  }

}
