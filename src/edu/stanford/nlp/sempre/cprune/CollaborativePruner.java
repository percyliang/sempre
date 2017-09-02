package edu.stanford.nlp.sempre.cprune;

import java.io.*;
import java.util.*;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;

/**
 * Static class for collaborative pruning.
 */
public class CollaborativePruner {
  public static class Options {
    @Option(gloss = "Logging verbosity")
    public int verbose = 0;
    @Option(gloss = "K = Maximum number of nearest-neighbor examples to consider (-1 to use all examples so far)")
    public int maxNumNeighbors = -1;
    @Option(gloss = "Load cached neighbors from this file")
    public String neighborFilePath = null;
    @Option(gloss = "Maximum number of matching patterns (default = use all patterns)")
    public int maxPredictedPatterns = Integer.MAX_VALUE;
    @Option(gloss = "Maximum number of derivations per example")
    public int maxDerivations = 5000;
    @Option(gloss = "Maximum number of times to fall back to exploration")
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
  static Map<String, List<String>> uidToCachedNeighbors;
  // uid => pattern
  static Map<String, FormulaPattern> consistentPattern = new HashMap<>();
  // patternString => customRuleString
  static Map<String, Set<String>> customRules = new HashMap<>();
  // set of patternStrings
  static Set<String> allConsistentPatterns = new HashSet<>();

  // Example-level variables
  public static boolean foundConsistentDerivation = false;
  public static Map<String, FormulaPattern> predictedPatterns;
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
    LogInfo.begin_track("Loading cached neighbors from %s", opts.neighborFilePath);
    uidToCachedNeighbors = new HashMap<>();
    try {
      BufferedReader reader = IOUtils.openIn(opts.neighborFilePath);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split("\t");
        String uid = tokens[0];
        String[] nids = tokens[1].split(",");
        uidToCachedNeighbors.put(uid, Arrays.asList(nids));
      }
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LogInfo.end_track();
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
    Map<String, FormulaPattern> patternFreqMap = new HashMap<>();
    List<String> cachedNeighbors = uidToCachedNeighbors.get(ex.id);
    int total = 0;

    // Gather the neighbors
    if (opts.maxNumNeighbors > 0) {
      for (String nid : cachedNeighbors) {
        // Only get examples that have been previously processed + found a consistent formula
        if (!consistentPattern.containsKey(nid))
          continue;

        String neighborPattern = consistentPattern.get(nid).pattern;
        if (!patternFreqMap.containsKey(neighborPattern))
          patternFreqMap.put(neighborPattern, new FormulaPattern(neighborPattern, 0));
        patternFreqMap.get(neighborPattern).frequency++;
        total++;
        if (total >= opts.maxNumNeighbors)
          break;
      }
    } else {
      for (String patternString : allConsistentPatterns) {
        patternFreqMap.put(patternString, new FormulaPattern(patternString, 1));
      }
    }

    // Sort by frequency (more frequent = smaller; see FormulaPattern.compareTo)
    List<Map.Entry<String, FormulaPattern>> patternFreqEntries = new ArrayList<>(patternFreqMap.entrySet());
    patternFreqEntries.sort(new ValueComparator<>(false));

    // Gather the patterns
    LogInfo.begin_track("Predicted patterns");
    int rank = 0;
    Set<String> predictedRulesStrings = new HashSet<>();
    predictedPatterns = new HashMap<>();
    for (Map.Entry<String, FormulaPattern> entry : patternFreqEntries) {
      FormulaPattern newPattern = entry.getValue();
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
      return FormulaPattern.convertToIndexedPattern(deriv);
    }
  }

  public static void addRules(String patternString, Derivation deriv, Example ex) {
    if (!customRules.containsKey(patternString)) {
      customRules.put(patternString, new HashSet<String>());
    }
    Set<String> parsedCustomRules = customGrammar.addCustomRule(deriv, ex);
    customRules.get(patternString).addAll(parsedCustomRules);
  }

  /**
   * Get called when a (consistent) formula is found.
   * Update the consistent patterns.
   */
  public static void updateConsistentPattern(ValueEvaluator evaluator, Example ex, Derivation deriv) {
    String uid = ex.id;
    if (ex.targetValue != null)
      deriv.compatibility = evaluator.getCompatibility(ex.targetValue, deriv.value);

    if (deriv.isRootCat() && deriv.compatibility == 1) {
      foundConsistentDerivation = true;
      LogInfo.logs("Found consistent deriv: %s", deriv);

      String patternString = getPatternString(deriv);
      FormulaPattern newConsistentPattern = new FormulaPattern(patternString, 0);
      newConsistentPattern.score = deriv.getScore();

      FormulaPattern oldConsistentPattern = consistentPattern.get(uid);
      if (oldConsistentPattern == null || newConsistentPattern.score > oldConsistentPattern.score) {
        addRules(patternString, deriv, ex);
        consistentPattern.put(uid, newConsistentPattern);
        allConsistentPatterns.add(patternString);
      }
    }
  }

  public static FormulaPattern getConsistentPattern(Example ex) {
    return consistentPattern.get(ex.id);
  }

}
