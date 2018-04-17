package edu.stanford.nlp.sempre.tables.alter;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.Value;
import edu.stanford.nlp.sempre.Values;

/**
 * Store information about equivalent classes inferred by Turked data for a single Example.
 *
 * Fields:
 *   - Example ID
 *   - Number of derivations from dump file
 *   - Number of equivalent classes
 *   - All tables that have been sent to Turk
 *   - All tables with agreed answers in Turked data
 *   - Original table verification
 *     - Target value from dataset (as Value)
 *     - Target value from Turk (as Value, or null if disagreed)
 *     - Whether the target values match
 *   - Matching equivalent classes
 *     - Number of derivations matched
 *     - Number of classes matched
 *   - Tables that can be Turked to distinguish the remaining classes
 *
 * @author ppasupat
 */
public class TurkEquivalentClassInfo {

  public static final String[] FIELD_NAMES = new String[] {
    "id", "numDerivs", "numClasses", "allTurkedTables", "agreedTurkedTables",
    "origTableTarget", "origTableTurkedTarget", "origTableFlag",
    "numDerivsMatched", "numClassesMatched"
  };

  public static String getHeader() {
    return String.join("\t", FIELD_NAMES);
  }

  public static void dumpHeader(PrintWriter writer) {
    writer.println(getHeader());
    writer.flush();
  }

  public String id;
  public List<Integer> allTurkedTables, agreedTurkedTables;
  public Value origTableTarget, origTableTurkedTarget;
  public String origTableFlag;
  public int numDerivs, numClasses, numDerivsMatched, numClassesMatched;

  @Override
  public String toString() {
    String[] fields = new String[FIELD_NAMES.length];
    fields[0] = id;
    fields[1] = "" + numDerivs;
    fields[2] = "" + numClasses;
    fields[3] = "" + allTurkedTables;
    fields[4] = "" + agreedTurkedTables;
    fields[5] = origTableTarget == null ? "null" : origTableTarget.toString();
    fields[6] = origTableTurkedTarget == null ? "null" : origTableTurkedTarget.toString();
    fields[7] = origTableFlag;
    fields[8] = "" + numDerivsMatched;
    fields[9] = "" + numClassesMatched;
    return String.join("\t", fields);
  }

  public void dump(PrintWriter writer) {
    writer.println(toString());
    writer.flush();
  }

  // ============================================================
  // Read from String or file
  // ============================================================

  public static TurkEquivalentClassInfo fromString(String line) {
    TurkEquivalentClassInfo info = new TurkEquivalentClassInfo();
    String[] fields = line.split("\t");
    info.id = fields[0];
    info.numDerivs = Integer.parseInt(fields[1]);
    info.numClasses = Integer.parseInt(fields[2]);
    info.allTurkedTables = readIntegerList(fields[3]);
    info.agreedTurkedTables = readIntegerList(fields[4]);
    info.origTableTarget = readValue(fields[5]);
    info.origTableTurkedTarget = readValue(fields[6]);
    info.origTableFlag = fields[7];
    info.numDerivsMatched = Integer.parseInt(fields[8]);
    info.numClassesMatched = Integer.parseInt(fields[9]);
    return info;
  }

  private static List<Integer> readIntegerList(String x) {
    x = x.replaceAll("\\[|\\]", "").trim();
    List<Integer> answer = new ArrayList<>();
    if (!x.isEmpty())
      for (String y : x.split(","))
        answer.add(Integer.parseInt(y.trim()));
    Collections.sort(answer);
    return answer;
  }

  private static Value readValue(String x) {
    if ("null".equals(x)) return null;
    if ("ERROR".equals(x)) return ValueCanonicalizer.ERROR;
    return Values.fromString(x);
  }

  public static Map<String, TurkEquivalentClassInfo> fromFile(String filename) {
    Map<String, TurkEquivalentClassInfo> map = new HashMap<>();
    try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
      String line;
      while ((line = in.readLine()) != null) {
        if (line.startsWith("id\tnumDerivs")) continue;   // Skip header
        TurkEquivalentClassInfo info = TurkEquivalentClassInfo.fromString(line);
        map.put(info.id, info);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return map;
  }

}
