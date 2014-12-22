package edu.stanford.nlp.sempre.freebase.utils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import edu.stanford.nlp.io.IOUtils;
import fig.basic.LogInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Utils for handling freebase data dump files
 *
 * @author jonathanberant
 */
public final class FreebaseUtils {
  private FreebaseUtils() { }

  public static final Pattern DELIMITER_PATTERN = Pattern.compile("\t");
  public static final String NAME_PROPERTY = "/type/object/name";
  public static final String ALIAS_PROPERTY = "/common/topic/alias";
  public static final String TYPE_PROPERTY = "/type/object/type";
  public static final String PROFESSION_PROPERTY = "/people/person/profession";
  public static final String MID_PREFIX = "/m/";
  public static final String COMMON_DOMAIN_PREFIX = "/common/";
  public static final String USER_DOMAIN_PREFIX = "/user/";
  public static final String BASE_DOMAIN_PREFIX = "/base/";
  public static final String FREEBASE_DOMAIN_PREFIX = "/freebase/";
  public static final String DATA_DOMAIN_PREFIX = "/dataworld/";
  public static final String TYPE_DOMAIN_PREFIX = "/type/";
  // indices in data dump file
  public static final int MID_INDEX = 0;
  public static final int PROPERTY_INDEX = 1;
  public static final int LANGUAGE_INDEX = 2;
  public static final int VALUE_INDEX = 2;
  public static final int DATE_INDEX = 3;
  public static final int NAME_INDEX = 3;

  /** Checks if a string is a valid MID */
  public static boolean isMid(String str) {
    return str.startsWith("/m/");
  }

  public static boolean isValidPropertyLine(String line) {

    String[] tokens = DELIMITER_PATTERN.split(line);

    return tokens.length == 3 &&
        isMid(tokens[MID_INDEX]) &&
        isMid(tokens[VALUE_INDEX]) &&
        !isMid(tokens[PROPERTY_INDEX]) &&
        isValidTypePrefix(tokens[PROPERTY_INDEX]);
  }

  public static boolean isValidPropertyLineWithDate(String line) {

    String[] tokens = DELIMITER_PATTERN.split(line);

    boolean regularProperty = tokens.length == 3 &&
        isMid(tokens[MID_INDEX]) &&
        !isMid(tokens[PROPERTY_INDEX]) &&
        isValidTypePrefix(tokens[PROPERTY_INDEX]);

    boolean dateProperty =
        tokens.length == 4 &&
            isMid(tokens[MID_INDEX]) &&
            isValidTypePrefix(tokens[PROPERTY_INDEX]) &&
            tokens[2].equals("") &&
            isDate(tokens[3]);

    return regularProperty || dateProperty;
  }

  public static boolean isDate(String dateCandidate) {

    boolean res = true;
    if (dateCandidate.startsWith("-")) {
      dateCandidate = dateCandidate.substring(1);
    }
    int i = 0;
    for (; i < Math.min(4, dateCandidate.length()); ++i) {
      if (!Character.isDigit(dateCandidate.charAt(i))) {
        res = false;
        break;
      }
    }
    if (i != 4)
      res = false;
    if (dateCandidate.length() > 4 && dateCandidate.charAt(4) != '-')
      res = false;
    return res;
  }

  public static String extractDate(String dateCandidate) {

    boolean neg = false;
    if (dateCandidate.startsWith("-")) {
      neg = true;
      dateCandidate = dateCandidate.substring(1);
    }
    return neg ? "-" + dateCandidate.substring(0, 4) : dateCandidate.substring(0, 4);
  }

  public static boolean isValidTypePrefix(String type) {
    if (type.equals("/type/datetime"))
      return true;
    return !(type.startsWith(BASE_DOMAIN_PREFIX) || type.startsWith(MID_PREFIX) || type.startsWith(COMMON_DOMAIN_PREFIX) ||
        type.startsWith(USER_DOMAIN_PREFIX) ||
        type.startsWith(DATA_DOMAIN_PREFIX) ||
        type.startsWith(FREEBASE_DOMAIN_PREFIX) ||
        type.startsWith(TYPE_DOMAIN_PREFIX) ||
        type.startsWith("/guid/"));
  }

  public static String getNoPrefixMid(String line) {
    return DELIMITER_PATTERN.split(line)[MID_INDEX].substring(MID_PREFIX.length());
  }

  public static String getProperty(String line) {
    return DELIMITER_PATTERN.split(line)[PROPERTY_INDEX];
  }

  public static String getValue(String line) {
    return DELIMITER_PATTERN.split(line)[VALUE_INDEX];
  }

  public static String getDateValue(String line) {
    return DELIMITER_PATTERN.split(line)[DATE_INDEX];
  }

  public static String getName(String line) {
    return DELIMITER_PATTERN.split(line)[NAME_INDEX];
  }

  public static boolean isArg1Equal2Mid(String mid, String tupleTokens) {
    return DELIMITER_PATTERN.split(tupleTokens)[MID_INDEX].equals(mid);
  }

  public static Map<String, String> loadMid2NameMap(String filename) throws IOException {

    LogInfo.log("Loading mid to name file...");

    Map<String, String> res = new HashMap<String, String>();
    BufferedReader reader = IOUtils.getBufferedFileReader(filename);
    String line;
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split("\t");
      res.put(tokens[0], tokens[1]);
    }
    LogInfo.log("Loaded " + res.keySet().size() + " MIDs");
    return res;
  }

  public static BiMap<Short, String> loadProperties(String propertyFileName) throws IOException {

    BiMap<Short, String> res = HashBiMap.create();
    BufferedReader reader = IOUtils.getBufferedFileReader(propertyFileName);

    String line;
    short id = 1;
    while ((line = reader.readLine()) != null) {
      res.put(id++, line);
    }
    return res;
  }

  public static boolean isUnary(String property) {
    property = FormatConverter.fromDotToSlash(property);
    return (property.equals(TYPE_PROPERTY) || property.equals(PROFESSION_PROPERTY));
  }

  public static boolean isNameProperty(String property) {
    property = FormatConverter.fromDotToSlash(property);
    return (property.equals(ALIAS_PROPERTY) || property.equals(NAME_PROPERTY));
  }
}
