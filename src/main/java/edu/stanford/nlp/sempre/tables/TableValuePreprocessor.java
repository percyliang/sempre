package edu.stanford.nlp.sempre.tables;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

public class TableValuePreprocessor extends TargetValuePreprocessor {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbose = 0;
    @Option(gloss = "Read preprocessed values from these .tagged files")
    public List<String> taggedFiles = new ArrayList<>();
  }
  public static Options opts = new Options();

  @Override
  public Value preprocess(Value value, Example ex) {
    if (!opts.taggedFiles.isEmpty() && ex != null) {
      return getFromTaggedFile(ex.id);
    }
    if (value instanceof ListValue) {
      List<Value> values = new ArrayList<>();
      for (Value entry : ((ListValue) value).values) {
        values.add(preprocessSingle(entry));
      }
      return new ListValue(values);
    } else {
      return preprocessSingle(value);
    }
  }

  public Value preprocessSingle(Value origTarget) {
    if (origTarget instanceof DescriptionValue) {
      String origString = ((DescriptionValue) origTarget).value;
      Value canonical = canonicalize(origString);
      if (opts.verbose >= 1)
        LogInfo.logs("Canonicalize %s --> %s", origString, canonical);
      return canonical;
    } else {
      return origTarget;
    }
  }

  /*
   * Most common origString patterns:
   * - number (4, 20, "4,000", 1996, ".15")
   * - number range ("1997/98", "2000-2005")
   * - number + unit ("4 years", "82.6 m")
   * - ordinal ("1st")
   * - date ("January 4, 1994", "7 August 2004", "9-1-1990")
   * - time -- point or amount ("4:47", "
   * - short strings (yes, no, more, less, before, after)
   * - string ("Poland", "World Championship")
   */
  protected Value canonicalize(String origString) {
    Value answer;
    LanguageInfo languageInfo = LanguageAnalyzer.getSingleton().analyze(origString);
    // Try converting to a number.
    answer = StringNormalizationUtils.parseNumberStrict(origString);
    if (answer != null) return answer;
    //answer = StringNormalizationUtils.parseNumberWithLanguageAnalyzer(languageInfo);
    //if (answer != null) return answer;
    // Try converting to a date.
    answer = StringNormalizationUtils.parseDate(origString);
    if (answer != null) return answer;
    answer = StringNormalizationUtils.parseDateWithLanguageAnalyzer(languageInfo);
    if (answer != null) return answer;
    // Maybe it's number + unit
    answer = StringNormalizationUtils.parseNumberWithUnitStrict(origString);
    if (answer != null) return answer;
    // Just treat as a description string
    return new DescriptionValue(origString);
  }

  // ============================================================
  // Get preprocessed value from tagged file
  // ============================================================

  Map<String, Value> idToValue = null;

  public Value getFromTaggedFile(String id) {
    if (idToValue == null) readTaggedFiles();
    return idToValue.get(id);
  }

  protected void readTaggedFiles() {
    LogInfo.begin_track("Reading .tagged files");
    idToValue = new HashMap<>();
    for (String path : opts.taggedFiles) {
      File file = new File(path);
      if (file.isDirectory()) {
        for (File subpath : file.listFiles())
          readTaggedFile(subpath.toString());
      } else {
        readTaggedFile(path);
      }
    }
    LogInfo.logs("Read %d entries", idToValue.size());
    LogInfo.end_track();
  }

  protected void readTaggedFile(String path) {
    LogInfo.begin_track("Reading %s", path);
    try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
      // Read header
      String[] header = reader.readLine().split("\t", -1);
      int exIdIndex = 0, targetCanonIndex = 0;
      while (!"id".equals(header[exIdIndex]))
        exIdIndex++;
      while (!"targetCanon".equals(header[targetCanonIndex]))
        targetCanonIndex++;
      // Read each line
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split("\t", -1);     // Include trailing spaces
        String[] rawValues = fields[targetCanonIndex].split("\\|");
        List<Value> values = new ArrayList<>();
        for (String rawValue : rawValues) {
          values.add(simpleCanonicalize(rawValue));
        }
        idToValue.put(fields[exIdIndex], new ListValue(values));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LogInfo.end_track();
  }

  /**
   * Like canonicalize, but assume that the string is already well-formed:
   * - A number should look like a float
   * - A date should be in the ISO format
   * - Otherwise, the value is treated as a string.
   */
  protected Value simpleCanonicalize(String origString) {
    Value answer;
    // Try converting to a number.
    answer = StringNormalizationUtils.parseNumberStrict(origString);
    if (answer != null) return answer;
    // Try converting to a date.
    answer = StringNormalizationUtils.parseDate(origString);
    if (answer != null) return answer;
    // Just treat as a description string
    return new DescriptionValue(origString);
  }

}
