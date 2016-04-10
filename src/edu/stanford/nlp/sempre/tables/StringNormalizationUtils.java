package edu.stanford.nlp.sempre.tables;

import java.text.*;
import java.util.*;
import java.util.regex.*;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import edu.stanford.nlp.sempre.DateValue;
import edu.stanford.nlp.sempre.LanguageAnalyzer;
import edu.stanford.nlp.sempre.LanguageInfo;
import edu.stanford.nlp.sempre.NameValue;
import edu.stanford.nlp.sempre.NumberValue;
import edu.stanford.nlp.sempre.StringValue;
import edu.stanford.nlp.sempre.Value;
import fig.basic.*;

/**
 * Utilities for string normalization.
 *
 * @author ppasupat
 */
public final class StringNormalizationUtils {
  public static class Options {
    @Option(gloss = "Use language analyzer") public boolean useLanguageAnalyzer = true;
    @Option(gloss = "Verbosity") public int verbose = 0;
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
    for (TableCell cell : column.children) {
      if (!cell.properties.metadata.isEmpty()) continue;  // Already analyzed.
      analyzeString(cell.properties.originalString, cell.properties.metadata);
    }
  }

  // ============================================================
  // Cell normalization
  // ============================================================

  public static final Pattern DASH = Pattern.compile("\\s*[-‐‑⁃‒–—―/,:;]\\s*");
  public static final Pattern SPACE = Pattern.compile("\\s+");

  public static void analyzeString(String o, Map<Value, Value> metadata) {
    metadata.clear();
    Value value;
    LanguageAnalyzer analyzer = LanguageAnalyzer.getSingleton();
    LanguageInfo languageInfo = analyzer.analyze(o);
    // ===== Number: Also handle "2,000 ft." --> (number 2000)
    value = parseNumberLenient(o);
    if (value == null && opts.useLanguageAnalyzer)
      value = parseNumberWithLanguageAnalyzer(languageInfo);
    if (value != null) metadata.put(TableTypeSystem.CELL_NUMBER_VALUE, value);
    // ===== Date and Time
    value = parseDate(o);
    if (value == null && opts.useLanguageAnalyzer)
      value = parseDateWithLanguageAnalyzer(languageInfo);
    if (value != null) metadata.put(TableTypeSystem.CELL_DATE_VALUE, value);
    // ===== First and Second: "2-1" --> first = (number 2), second = (number 1)
    // TODO(ice): Do we want non-numeric stuff?
    String[] dashSplitted = DASH.split(o);
    if (dashSplitted.length == 2) {
      NumberValue first = parseNumberStrict(dashSplitted[0]), second = parseNumberStrict(dashSplitted[1]);
      if (first != null && second != null) {
        //metadata.put(TableTypeSystem.CELL_FIRST_VALUE, first);
        metadata.put(TableTypeSystem.CELL_SECOND_VALUE, second);
      }
    }
    // ===== Unit: "2,000 ft." --> "ft."
    // TODO(ice): This is very crude
    String[] spaceSplitted = SPACE.split(o);
    if (spaceSplitted.length == 2) {
      if (parseNumberStrict(spaceSplitted[0]) != null)
        metadata.put(TableTypeSystem.CELL_UNIT_VALUE, new StringValue(spaceSplitted[1]));
    }
    // ===== Normalize
    metadata.put(TableTypeSystem.CELL_NORMALIZED_VALUE, new StringValue(aggressiveNormalize(o)));
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
      Number parsed = numberFormat.parse(s.replace(" ", ""));
      return new NumberValue(parsed.doubleValue());
    } catch (ParseException e) {
      return null;
    }
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

  public static final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("MMM d, yyyy");

  /**
   * Convert string to DateValue.
   */
  public static DateValue parseDate(String s) {
    try {
      DateTime date = dateFormat.parseDateTime(s);
      return new DateValue(date.getYear(), date.getMonthOfYear(), date.getDayOfMonth());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static final Pattern suTimeDateFormat = Pattern.compile("([0-9X]{4})(?:-([0-9X]{2}))?(?:-([0-9X]{2}))?");

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
   * Convert escaped characters to actual values
   */
  public static String unescape(String x) {
    return x.replaceAll("\\\\n", "\n");
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
  public static NumberValue nameValueToNumberValue(NameValue v) {
    if (v.description == null) return null;
    try {
      Number result = numberFormat.parse(v.description);
      return new NumberValue(result.doubleValue());
    } catch (ParseException e) {
      return null;
    }
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
        .replaceAll("[‐‑–—]", "-")
        .replaceAll("[\\u2E00-\\uFFFF]", "");     // (Sorry Chinese people)
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
    // Year in parentheses
    string = string.replaceAll("\\(\\d* ?-? ?\\d*\\)", "");
    // Outside Quote
    string = string.replaceAll("^\"(.*)\"$", "$1");
    // Numbering
    if (!string.matches("^[0-9.]+$"))
      string = string.replaceAll("^\\d+\\.", "");
    return string.replaceAll("\\s+", " ").trim();
  }

  /**
   * More aggressive normalization. (Include simple and whitespace normalization)
   */
  public static String aggressiveNormalize(String string) {
    // Dashed / Parenthesized information
    string = simpleNormalize(string);
    string = string.replaceAll("\\[[^\\]]*\\]", "");
    string = string.replaceAll("[\\u007F-\\uFFFF]", "");
    string = string.trim().replaceAll(" - .*$", "");
    string = string.trim().replaceAll("\\([^)]*\\)$", "");
    return string.replaceAll("\\s+", " ").trim();
  }

  // ============================================================
  // Test
  // ============================================================

  private static void unitTest(String o) {
    Map<Value, Value> metadata = new HashMap<>();
    analyzeString(o, metadata);
    LogInfo.logs("%s %s", o, metadata);
  }

  public static void main(String[] args) {
    LanguageAnalyzer.opts.languageAnalyzer = "corenlp.CoreNLPAnalyzer";
    opts.verbose = 2;
    unitTest("2");
    unitTest("twenty three");
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
    unitTest("from dawn to dusk");
    unitTest("January 4 - February 10");
    unitTest("July 500");
    unitTest("July 500 B.C.");
    unitTest("1800s");
    unitTest("19th century");
  }

}
