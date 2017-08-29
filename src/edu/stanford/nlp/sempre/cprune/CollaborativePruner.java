package edu.stanford.nlp.sempre.cprune;

import java.io.*;
import java.util.*;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;

/**
 * Static class for collaborative pruning.
 */
public class CollaborativePruner {
  public static class Options {
    @Option(gloss = "K = Maximum number of nearest-neighbor examples to consider (-1 to use all examples so far)")
    public int maxNumNeighbors = -1;
    @Option(gloss = "Load cached neighbors from this file")
    public String neighborFilePath = null;
    @Option(gloss = "Maximum number of matching patterns (default = use all patterns)")
    public int maxPredictedPatterns = Integer.MAX_VALUE;
    @Option(gloss = "Maximum number of derivations per example")
    public int maxDerivations = 5000;
    @Option(gloss = "Maximum number of exporation iterations")
    public int maxExplorationIters = Integer.MAX_VALUE;
  }

  public static Options opts = new Options();

  public enum Mode { EXPLORE, EXPLOIT, NONE }

  public static Mode mode = Mode.NONE;
  public static CPruneStats stats = new CPruneStats();
  public static CustomGrammar customGrammar = new CustomGrammar();

  // Static class; do not instantiate
  private CollaborativePruner() { throw new RuntimeException("Cannot instantiate CollaborativePruner"); }

  // Global variables
  // Nearest neighbors
  static Map<String, List<String>> neighbors;
  // uid => pattern
  static Map<String, Pattern> consistentPattern = new HashMap<>();
  // patternString => customRuleString
  static Map<String, Set<String>> customRules = new HashMap<>();
  // set of patternStrings
  static Set<String> allConsistentPatterns = new HashSet<>();

  // Example-level variables
  public static boolean foundConsistentDerivation = false;
  public static Map<String, Pattern> predictedPatterns;
  public static List<Rule> predictedRules;

  /**
   * Read the cached neighbors file.
   * Line Format: ex_id [tab] neighbor_id1,neighbor_id2,...
   */
  public static void loadNeighbors() {
    if (opts.neighborFilePath == null) {
      LogInfo.logs("neighborFilePath is null.");
      return;
    }
    LogInfo.logs("loading Neighbors " + opts.neighborFilePath);
    Map<String, List<String>> tmpMap = new HashMap<>();
    try {
      BufferedReader reader = IOUtils.openIn(opts.neighborFilePath);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split("\t");
        String uid = tokens[0];
        String[] nids = tokens[1].split(",");
        tmpMap.put(uid, Arrays.asList(nids));
      }
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    neighbors = tmpMap;
  }

  public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
    List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
      public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
        return (o1.getValue()).compareTo(o2.getValue());
      }
    });

    Map<K, V> result = new LinkedHashMap<K, V>();
    for (Map.Entry<K, V> entry : list) {
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  public static void initialize(Example ex, Mode mode) {
    CollaborativePruner.mode = mode;
    predictedRules = null;
    predictedPatterns = null;
    foundConsistentDerivation = false;
    if (mode == Mode.EXPLOIT) {
      preprocessExample(ex);
    }
  }

  static void preprocessExample(Example ex) {
    Map<String, Pattern> patternFreqMap = new HashMap<>();
    List<String> cachedNeighbors = neighbors.get(ex.id);
    int total = 0;

    // Gather the neighbors
    if (opts.maxNumNeighbors > 0) {
      for (String nid : cachedNeighbors) {
        // Only get examples that have been previously processed
        if (!consistentPattern.containsKey(nid))
          continue;

        String neighborPattern = consistentPattern.get(nid).pattern;
        if (!patternFreqMap.containsKey(neighborPattern))
          patternFreqMap.put(neighborPattern, new Pattern(neighborPattern, 0, 0));
        patternFreqMap.get(neighborPattern).frequency += 1;
        total++;
        if (total >= opts.maxNumNeighbors)
          break;
      }
    } else {
      for (String patternString : allConsistentPatterns) {
        patternFreqMap.put(patternString, new Pattern(patternString, 0, 1));
      }
    }

    // Gather the patterns
    patternFreqMap = sortByValue(patternFreqMap);
    int rank = 0;
    Set<String> predictedRulesStrings = new HashSet<>();
    predictedPatterns = new HashMap<>();
    LogInfo.begin_track("Predicted patterns");
    for (Map.Entry<String, Pattern> entry : patternFreqMap.entrySet()) {
      Pattern newPattern = entry.getValue();
      predictedPatterns.put(newPattern.pattern, newPattern);
      predictedRulesStrings.addAll(customRules.get(newPattern.pattern));
      LogInfo.logs((rank + 1) + ". " + newPattern.pattern + " (" + newPattern.frequency + ")");
      rank++;
      if (rank >= opts.maxPredictedPatterns)
        break;
    }

    // Gather the rules
    predictedRules = customGrammar.getRules(predictedRulesStrings);
    LogInfo.end_track();
  }

  public static String getPatternString(Derivation deriv) {
    if (deriv.cat.equals("$TOKEN") || deriv.cat.equals("$PHRASE")
        || deriv.cat.equals("$LEMMA_TOKEN") || deriv.cat.equals("$LEMMA_PHRASE")) {
      return deriv.cat;
    } else {
      return PatternInfo.convertToIndexedPattern(deriv);
    }
  }

  public static void addRules(String patternString, Derivation deriv, Example ex) {
    if (!customRules.containsKey(patternString)) {
      customRules.put(patternString, new HashSet<String>());
    }
    Set<String> parsedCustomRules = customGrammar.addCustomRule(deriv, ex);
    customRules.get(patternString).addAll(parsedCustomRules);
  }

  public static void updateConsistentPattern(ValueEvaluator evaluator, Example ex, Derivation deriv) {
    String uid = ex.id;
    if (ex.targetValue != null)
      deriv.compatibility = evaluator.getCompatibility(ex.targetValue, deriv.value);

    if (deriv.isRootCat() && deriv.compatibility == 1) {
      foundConsistentDerivation = true;
      LogInfo.logs("Found consistent deriv: %s", deriv);

      String patternString = getPatternString(deriv);
      Pattern newConsistentPattern = new Pattern(patternString, 0, 0);
      newConsistentPattern.score = deriv.getScore();

      if (!consistentPattern.containsKey(uid)) {
        addRules(patternString, deriv, ex);
        consistentPattern.put(uid, newConsistentPattern);
        allConsistentPatterns.add(patternString);
      } else {
        Pattern oldConsistentPattern = consistentPattern.get(uid);
        if (newConsistentPattern.score > oldConsistentPattern.score) {
          addRules(patternString, deriv, ex);
          consistentPattern.put(uid, newConsistentPattern);
          allConsistentPatterns.add(patternString);
        }
      }
    }
  }

  public static Pattern getConsistentPattern(Example ex) {
    return consistentPattern.get(ex.id);
  }

  public static Pattern getPattern(Example ex, Derivation deriv) {
    if (mode == Mode.EXPLORE)
      return null;
    if (!deriv.isRootCat())
      return null;
    return predictedPatterns.get(getPatternString(deriv));
  }
}
