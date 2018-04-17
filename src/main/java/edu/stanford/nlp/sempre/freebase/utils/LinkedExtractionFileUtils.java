package edu.stanford.nlp.sempre.freebase.utils;

import edu.stanford.nlp.io.IOUtils;
import fig.basic.LogInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public final class LinkedExtractionFileUtils {

  private String extractionFile;
  private static final int ARG1_INDEX = 0;
  private static final int PREDICATE_INDEX = 1;
  private static final int ARG2_INDEX = 2;
  private static final int MID_INDEX = 3;
  public static final Pattern DELIMITER_PATTERN = Pattern.compile("\t");
  public static final String TIME_ARG = "TIME:";


  public LinkedExtractionFileUtils(String extractionFileName) {
    this.extractionFile = extractionFileName;
  }

  public Map<String, Set<String>> getIdToExtractionsMap() throws IOException {

    LogInfo.log("Uploading id-to-extraction-set map");
    Map<String, Set<String>> res = new HashMap<String, Set<String>>();

    BufferedReader reader = IOUtils.getBufferedFileReader(extractionFile);
    String line;
    while ((line = reader.readLine()) != null) {

      String[] tokens = DELIMITER_PATTERN.split(line);

      Set<String> extractionSet = res.get(tokens[MID_INDEX]);
      if (extractionSet == null) {
        extractionSet = new HashSet<String>();
        res.put(tokens[MID_INDEX], extractionSet);
      }
      extractionSet.add(tokens[ARG1_INDEX] + DELIMITER_PATTERN + tokens[PREDICATE_INDEX] + DELIMITER_PATTERN + DELIMITER_PATTERN + tokens[ARG2_INDEX]);
    }
    reader.close();
    LogInfo.log("Done uploading id-to-extraction-set map");
    return res;
  }

  public Set<String> getLinkedIdSet() throws IOException {

    LogInfo.log("Uploading linked MIDs set");
    Set<String> res = new HashSet<String>();

    BufferedReader reader = IOUtils.getBufferedFileReader(extractionFile);
    String line;
    while ((line = reader.readLine()) != null) {

      String[] tokens = DELIMITER_PATTERN.split(line);
      res.add(tokens[MID_INDEX]);
    }
    reader.close();
    LogInfo.log("Done uploading linked IDs set");
    return res;
  }

  public Map<String, Map<String, List<String>>> getIdToArg2ToPredicateListMap() throws IOException {

    LogInfo.begin_track("Uploading id-to-arg-predicate-list-map");
    // BinaryNormalizer normalizer = new BinaryNormalizer();

    Map<String, Map<String, List<String>>> res = new HashMap<String, Map<String, List<String>>>();

    for (String line : IOUtils.readLines(extractionFile)) {

      String[] tokens = DELIMITER_PATTERN.split(line);

      String id = tokens[MID_INDEX];
      String arg2 = tokens[ARG2_INDEX];
      String predicate = tokens[PREDICATE_INDEX];
      // String predicate = normalizer.normalize(tokens[PREDICATE_INDEX]);

      Map<String, List<String>> arg2ToPredicateList = res.get(id);
      if (arg2ToPredicateList == null) {
        arg2ToPredicateList = new HashMap<String, List<String>>();
        arg2ToPredicateList.put(arg2, new LinkedList<String>());
        res.put(id, arg2ToPredicateList);
      }

      List<String> predicateList = arg2ToPredicateList.get(arg2);
      if (predicateList == null) {
        predicateList = new LinkedList<String>();
        arg2ToPredicateList.put(arg2, predicateList);
      }
      predicateList.add(predicate);
    }
    LogInfo.end_track();
    return res;
  }

  public static boolean isTimeArg(String str) {
    return str.startsWith(TIME_ARG);
  }

  public static String extractTime(String str) {
    if (!isTimeArg(str)) {
      throw new RuntimeException("Not a time arg: " + str);
    }
    return str.substring(TIME_ARG.length());
  }
}
