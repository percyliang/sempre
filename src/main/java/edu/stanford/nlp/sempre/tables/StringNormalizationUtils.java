package edu.stanford.nlp.sempre.tables;

import java.text.*;
import java.util.*;
import java.util.regex.*;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * Utilities for string normalization.
 *
 * @author ppasupat
 */
public final class StringNormalizationUtils {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbose = 0;
    @Option(gloss = "Use language analyzer")
    public boolean useLanguageAnalyzer = true;
    @Option(gloss = "NUMBER does not have to be at the beginning of the string")
    public boolean numberCanStartAnywhere = false;
    @Option(gloss = "NUM2 does not have to follow the pattern NUMBER DASH NUM2")
    public boolean num2CanStartAnywhere = false;
  }
  public static Options opts = new Options();

  private StringNormalizationUtils() { }    // Should not be instantiated.

  /**
   * Analyze the content of the cells in the same column, and then generate possible normalizations.
   * Modify the property map in each cell.
   *
   * TODO(ice): Take the homogeneity of the cells into account.
   */
  public static void analyzeColumn(TableColumn column) {
    // Parts in the same column with the same string content gets the same id.
    Map<String, String> originalStringToPartId = new HashMap<>();
    for (TableCell cell : column.children) {
      if (!cell.properties.metadata.isEmpty()) continue;  // Already analyzed.
      analyzeString(cell.properties.originalString, cell.properties.metadata,
          column, originalStringToPartId);
    }
  }

  // ============================================================
  // Cell normalization
  // ============================================================

  public static final Pattern STRICT_DASH = Pattern.compile("\\s*[-‐‑⁃‒–—―]\\s*");
  public static final Pattern DASH = Pattern.compile("\\s*[-‐‑⁃‒–—―/,:;]\\s*");
  public static final Pattern COMMA = Pattern.compile("\\s*(,\\s|\\n|/)\\s*");
  public static final Pattern SPACE = Pattern.compile("\\s+");

  public static void analyzeString(String o, Multimap<Value, Value> metadata,
      TableColumn column, Map<String, String> originalStringToPartId) {
    metadata.clear();
    Value value;
    LanguageAnalyzer analyzer = LanguageAnalyzer.getSingleton();
    LanguageInfo languageInfo = analyzer.analyze(o);
    // ===== Number: Also handle "2,000 ft." --> (number 2000) =====
    value = parseNumberLenient(o);
    if (value == null && opts.useLanguageAnalyzer)
      value = parseNumberWithLanguageAnalyzer(languageInfo);
    if (value != null) metadata.put(TableTypeSystem.CELL_NUMBER_VALUE, value);
    // ===== Date and Time =====
    value = parseDate(o);
    if (value == null && opts.useLanguageAnalyzer)
      value = parseDateWithLanguageAnalyzer(languageInfo);
    if (value != null) metadata.put(TableTypeSystem.CELL_DATE_VALUE, value);
    // ===== First and Second: "2-1" --> first = (number 2), second = (number 1) =====
    if (opts.num2CanStartAnywhere) {
      value = parseNum2Lenient(o);
      if (value != null)
        metadata.put(TableTypeSystem.CELL_NUM2_VALUE, value);
    } else {
      String[] splitted = DASH.split(o);
      if (splitted.length != 2) splitted = SPACE.split(o);
      if (splitted.length == 2) {
        NumberValue first = parseNumberStrict(splitted[0]), second = parseNumberStrict(splitted[1]);
        if (first != null && second != null) {
          metadata.put(TableTypeSystem.CELL_NUM2_VALUE, second);
        }
      }
    }
    // ===== List: "apple, banana, carrot" --> fb:part.apple, etc. =====
    String[] splitted = COMMA.split(o);
    if (splitted.length > 1) {
      for (String partName : splitted) {
        String normalizedPartName = StringNormalizationUtils.characterNormalize(partName).toLowerCase();
        String id = originalStringToPartId.get(normalizedPartName);
        if (id == null) {
          String canonicalName = TableTypeSystem.canonicalizeName(normalizedPartName);
          id = TableTypeSystem.getUnusedName(
              TableTypeSystem.getPartName(canonicalName, column.columnName),
              originalStringToPartId.values());
          originalStringToPartId.put(normalizedPartName, id);
        }
        metadata.put(TableTypeSystem.CELL_PART_VALUE, new NameValue(id, partName));
      }
    }
  }

  // ============================================================
  // Type Conversion
  // ============================================================

  public static final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  /**
   * Convert string to number.
   * Partial match is allowed: "9,000 cakes" --> 9000
   */
  public static NumberValue parseNumberLenient(String s) {
    try {
      if (opts.numberCanStartAnywhere)
        s = s.replaceAll("^[^0-9.]*", "");
      Number parsed = numberFormat.parse(s.replace(" ", ""));
      return new NumberValue(parsed.doubleValue());
    } catch (ParseException e) {
      return null;
    }
  }

  /**
   * Get the second number
   * Partial match is allowed: "9,000 cakes from 120 bakeries" --> 120
   */
  public static NumberValue parseNum2Lenient(String s) {
    s = s.replace(" ", "");
    if (opts.numberCanStartAnywhere)
      s = s.replaceAll("^[^0-9.]*", "");
    ParsePosition parsePosition = new ParsePosition(0);
    Number parsed = numberFormat.parse(s, parsePosition);
    if (parsed == null) return null;
    s = s.substring(parsePosition.getIndex());
    s = s.replaceAll("^[^0-9.]*", "");
    parsePosition.setIndex(0);
    parsed = numberFormat.parse(s, parsePosition);
    if (parsed == null) return null;
    return new NumberValue(parsed.doubleValue());
  }

  /**
   * Convert string to number.
   * Partial match is not allowed: "9,000 cakes" --> null
   */
  public static NumberValue parseNumberStrict(String s) {
    ParsePosition pos = new ParsePosition(0);
    Number parsed = numberFormat.parse(s, pos);
    if (parsed == null || s.length() != pos.getIndex()) return null;
    return new NumberValue(parsed.doubleValue());
  }

  /**
   * Convert string to number + unit.
   * Must exactly match the pattern "number unit" (e.g., "9,000 cakes")
   */
  public static NumberValue parseNumberWithUnitStrict(String s) {
    String[] tokens = s.split(" ");
    if (tokens.length != 2) return null;
    ParsePosition pos = new ParsePosition(0);
    Number parsed = numberFormat.parse(tokens[0], pos);
    if (parsed == null || tokens[0].length() != pos.getIndex()) return null;
    return new NumberValue(parsed.doubleValue(), tokens[1]);
  }

  public static NumberValue parseNumberWithLanguageAnalyzer(LanguageInfo languageInfo) {
    if (languageInfo.numTokens() == 0) return null;
    String nerSpan;
    nerSpan = languageInfo.getNormalizedNerSpan("NUMBER", 0, languageInfo.numTokens());
    if (nerSpan != null) {
      try {
        return new NumberValue(Double.parseDouble(nerSpan));
      } catch (NumberFormatException e) { }
    }
    nerSpan = languageInfo.getNormalizedNerSpan("ORDINAL", 0, languageInfo.numTokens());
    if (nerSpan != null) {
      try {
        return new NumberValue(Double.parseDouble(nerSpan));
      } catch (NumberFormatException e) { }
    }
    nerSpan = languageInfo.getNormalizedNerSpan("PERCENT", 0, languageInfo.numTokens());
    if (nerSpan != null) {
      try {
        return new NumberValue(Double.parseDouble(nerSpan.substring(1)));
      } catch (NumberFormatException e) { }
    }
    nerSpan = languageInfo.getNormalizedNerSpan("MONEY", 0, languageInfo.numTokens());
    if (nerSpan != null) {
      try {
        return new NumberValue(Double.parseDouble(nerSpan.substring(1)));
      } catch (NumberFormatException e) { }
    }
    return null;
  }

  public static final DateTimeFormatter americanDateFormat = DateTimeFormat.forPattern("MMM d, yyyy");
  public static final Pattern suTimeDateFormat = Pattern.compile("([0-9X]{4})(?:-([0-9X]{2}))?(?:-([0-9X]{2}))?");

  /**
   * Convert string to DateValue.
   */
  public static DateValue parseDate(String s) {
    Matcher matcher = suTimeDateFormat.matcher(s.toUpperCase());
    if (matcher.matches()) {
      String yS = matcher.group(1), mS = matcher.group(2), dS = matcher.group(3);
      int y = -1, m = -1, d = -1;
      if (!(yS == null || yS.isEmpty() || yS.contains("X"))) y = Integer.parseInt(yS);
      if (!(mS == null || mS.isEmpty() || mS.contains("X"))) m = Integer.parseInt(mS);
      if (!(dS == null || dS.isEmpty() || dS.contains("X"))) d = Integer.parseInt(dS);
      if (y == -1 && m == -1 && d == -1) return null;
      return new DateValue(y, m, d);
    }
    try {
      DateTime date = americanDateFormat.parseDateTime(s);
      return new DateValue(date.getYear(), date.getMonthOfYear(), date.getDayOfMonth());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static DateValue parseDateWithLanguageAnalyzer(LanguageInfo languageInfo) {
    if (languageInfo.numTokens() == 0) return null;
    String nerSpan = languageInfo.getNormalizedNerSpan("DATE", 0, languageInfo.numTokens());
    if (opts.verbose >= 2)
      LogInfo.logs("%s %s %s %s", languageInfo.tokens, languageInfo.nerTags, languageInfo.nerValues, nerSpan);
    if (nerSpan == null) return null;
    Matcher matcher = suTimeDateFormat.matcher(nerSpan);
    if (!matcher.matches()) return null;
    String yS = matcher.group(1), mS = matcher.group(2), dS = matcher.group(3);
    int y = -1, m = -1, d = -1;
    if (!(yS == null || yS.isEmpty() || yS.contains("X"))) y = Integer.parseInt(yS);
    if (!(mS == null || mS.isEmpty() || mS.contains("X"))) m = Integer.parseInt(mS);
    if (!(dS == null || dS.isEmpty() || dS.contains("X"))) d = Integer.parseInt(dS);
    if (y == -1 && m == -1 && d == -1) return null;
    return new DateValue(y, m, d);
  }

  // ============================================================
  // Generic String normalization
  // ============================================================

  /**
   * newline (=> `\n`), backslash (`\` => `\\`), and pipe (`|` => `\p`)
   */
  public static String escapeTSV(String x) {
    return x.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\p").replaceAll("\\s", " ").trim();
  }

  public static String unescapeTSV(String x) {
    return x.replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\");
  }

  /**
   * Collapse multiple spaces into one.
   */
  public static String whitespaceNormalize(String x) {
    return x.replaceAll("\\s", " ").trim();
  }

  /**
   * Remove ALL spaces and non-alphanumeric characters, then convert to lower case.
   * Used for fuzzy matching.
   */
  public static String collapseNormalize(String x) {
    return Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]", "").toLowerCase();
  }

  /**
   * String to number
   */
  public static NumberValue toNumberValue(String description) {
    if (description == null) return null;
    try {
      Number result = numberFormat.parse(description);
      return new NumberValue(result.doubleValue());
    } catch (ParseException e) {
      return null;
    }
  }

  public static NumberValue toNumberValue(Value value) {
    if (value instanceof NumberValue) return (NumberValue) value;
    if (value instanceof DateValue) {
      DateValue date = (DateValue) value;
      if (date.month == -1 && date.day == -1)
        return new NumberValue(date.year);
    }
    if (value instanceof NameValue) return toNumberValue(((NameValue) value).description);
    if (value instanceof DescriptionValue) return toNumberValue(((DescriptionValue) value).value);
    return null;
  }

  /**
   * Character normalization.
   */
  public static String characterNormalize(String string) {
    // Remove diacritics // (Sorry European people)
    string = Normalizer.normalize(string, Normalizer.Form.NFD).replaceAll("[\u0300-\u036F]", "");
    // Special symbols
    string = string
        .replaceAll("‚", ",")
        .replaceAll("„", ",,")
        .replaceAll("[·・]", ".")
        .replaceAll("…", "...")
        .replaceAll("ˆ", "^")
        .replaceAll("˜", "~")
        .replaceAll("‹", "<")
        .replaceAll("›", ">")
        .replaceAll("[‘’´`]", "'")
        .replaceAll("[“”«»]", "\"")
        .replaceAll("[•†‡]", "")
        .replaceAll("[-‐‑–—]", "-");
    return string.replaceAll("\\s+", " ").trim();
  }

  /**
   * Simple normalization. (Include whitespace normalization)
   */
  public static String simpleNormalize(String string) {
    string = characterNormalize(string);
    // Citation
    string = string.replaceAll("\\[(nb ?)?\\d+\\]", "");
    string = string.replaceAll("\\*+$", "");
    // Outside Quote
    string = string.replaceAll("^\"(.*)\"$", "$1");
    return string.replaceAll("\\s+", " ").trim();
  }

  /**
   * More aggressive normalization. (Include simple and whitespace normalization)
   */
  public static String aggressiveNormalize(String string) {
    // Dashed / Parenthesized information
    string = simpleNormalize(string);
    String oldString;
    do {
      oldString = string;
      // Remove citations
      string = string.trim().replaceAll("((?<!^)\\[[^\\]]*\\]|\\[\\d+\\]|[•♦†‡*#+])*$", "");
      // Remove details in parenthesis
      string = string.trim().replaceAll("(?<!^)(\\s*\\([^)]*\\))*$", "");
      // Remove outermost quotation mark
      string = string.trim().replaceAll("^\"([^\"]*)\"$", "$1");
    } while (!oldString.equals(string));
    // Collapse whitespaces
    return string.replaceAll("\\s+", " ").trim();
  }

  /**
   * Normalization scheme in the official Python evaluator.
   */
  public static String officialEvaluatorNormalize(String string) {
    // Remove diacritics
    string = Normalizer.normalize(string, Normalizer.Form.NFD).replaceAll("[\u0300-\u036F]", "");
    // Normalize quotes and dashes
    string = string
        .replaceAll("[‘’´`]", "'")
        .replaceAll("[“”]", "\"")
        .replaceAll("[‐‑‒–—−]", "-");
    String oldString;
    do {
      oldString = string;
      // Remove citations
      string = string.trim().replaceAll("((?<!^)\\[[^\\]]*\\]|\\[\\d+\\]|[•♦†‡*#+])*$", "");
      // Remove details in parenthesis
      string = string.trim().replaceAll("(?<!^)(\\s*\\([^)]*\\))*$", "");
      // Remove outermost quotation mark
      string = string.trim().replaceAll("^\"([^\"]*)\"$", "$1");
    } while (!oldString.equals(string));
    // Remove final '.'
    if (string.endsWith("."))
      string = string.substring(0, string.length() - 1);
    // Collapse whitespaces and convert to lower case
    string = string.replaceAll("\\s+", " ").toLowerCase().trim();
    return string;
  }

  // ============================================================
  // Test
  // ============================================================

  private static void unitTest(String o) {
    Multimap<Value, Value> metadata = ArrayListMultimap.create();
    TableColumn column = new TableColumn("Test", "test", 0);
    analyzeString(o, metadata, column, new HashMap<>());
    String aggressive = aggressiveNormalize(o).toLowerCase();
    String official = officialEvaluatorNormalize(o);
    LogInfo.logs("%s %s | %s %s %s", o, metadata, official, aggressive, aggressive.equals(official));
  }

  public static void main(String[] args) {
    LanguageAnalyzer.opts.languageAnalyzer = "corenlp.CoreNLPAnalyzer";
    opts.verbose = 2;
    opts.numberCanStartAnywhere = true;
    opts.num2CanStartAnywhere = true;
    unitTest("2");
    unitTest("twenty three");
    unitTest("apple, banana, banana, BANANA");
    unitTest("apple\nbanana\norange");
    unitTest("0-1\n(4-5 p)");
    unitTest("\"HELLO\"");
    unitTest("21st");
    unitTest("2001st");
    unitTest("2,000,000 ft.");
    unitTest("2,000,000.3579");
    unitTest("1/2");
    unitTest("1-2");
    unitTest("50%");
    unitTest("$30");
    unitTest("1 104");
    unitTest("United States of America (USA)");
    unitTest("320 bhp diesel, 10 knots (19 km/h)");
    unitTest("January 3, 1993");
    unitTest("July 2008");
    // normalized-annotated-200.examples
    unitTest("19 September 1984"); // Ex 90
    unitTest("July 9"); // Ex 139, 155
    unitTest("March 1983"); // Ex 167
    // Other things handled by SUTime
    unitTest("Friday");
    unitTest("Every Friday");
    unitTest("7:00");
    unitTest("7pm");
    unitTest("January 2, 7am");
    unitTest("morning");
    unitTest("1993-95");
    unitTest("Jan 2-5");
    unitTest("Jan 2 - 5");
    unitTest("Jan 2 - Feb 5");
    unitTest("from dawn to dusk");
    unitTest("January 4 - February 10");
    unitTest("July 500");
    unitTest("July 500 B.C.");
    unitTest("1800s");
    unitTest("19th century");
  }

}
