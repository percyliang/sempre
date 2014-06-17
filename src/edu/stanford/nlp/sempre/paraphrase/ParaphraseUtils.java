package edu.stanford.nlp.sempre.paraphrase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import fig.basic.IntPair;
import fig.basic.LogInfo;
import fig.basic.MapUtils;
import fig.basic.Pair;

public class ParaphraseUtils {
  
  public static Map<String,String> posCanonicMap = new HashMap<>();

  static {
    posCanonicMap.put("JJR", "JJ");
    posCanonicMap.put("JJS", "JJ");
    posCanonicMap.put("NNS", "NN");
    posCanonicMap.put("PRP$", "PRP");
    posCanonicMap.put("RBR", "RB");
    posCanonicMap.put("VBD", "VB");
    posCanonicMap.put("VBG", "VB");
    posCanonicMap.put("VBN", "VB");
    posCanonicMap.put("VBP", "VB");
    posCanonicMap.put("VBZ", "VB");
    posCanonicMap.put("WDT", "W");
    posCanonicMap.put("WP", "W");
    posCanonicMap.put("WP$", "W");
    posCanonicMap.put("WRB", "W");    
  }
  
  public static boolean posCompatible(String antecedentPos, String targetPos) {
    String antecedentCanonical = MapUtils.get(posCanonicMap, antecedentPos, antecedentPos);
    String targetCanonical = MapUtils.get(posCanonicMap, targetPos, targetPos);
    return antecedentCanonical.equals(targetCanonical);  }

  public static Pair<Context,Interval> extractContextIntervalPair(Derivation deriv, Example ex) {
    Interval interval = extractEntityInterval(deriv);
    String lhs = (0==interval.start) ? "" : ex.lemmaPhrase(0, interval.start);
    String rhs = (interval.end == ex.numTokens()) ? "" : ex.lemmaPhrase(interval.end, ex.numTokens());
    Context  context = new Context(lhs,rhs);
    return Pair.newPair(context, interval);
  }

  /**
   * This assumes that the derivation contains a single contiguous entity (which is true for entity.grammar)
   */
  private static Interval extractEntityInterval(Derivation deriv) {

    Interval res = new Interval(-1, -1);
    extractEntityIntervalRecurse(deriv,res);
    return res;
  }

  private static void extractEntityIntervalRecurse(Derivation deriv,
      Interval interval) {

    if(deriv.rule.getLhs() != null && deriv.rule.getLhs().equals("$Entity")) { //base
      interval.set(deriv.start, deriv.end);
      return;
    }
    for(Derivation child: deriv.children) { //recurse
      extractEntityIntervalRecurse(child,interval); 
    }
  }

  /**
   * Computes Levenshtein edit distance between two lists of Strings - stole from Spence that did this on CoreLabel
   */
  public static int editDistance(final List<String> l1, final List<String> l2) {
    int[][] m = new int[l1.size()+1][l2.size()+1];
    for(int i = 1; i <= l1.size(); i++)
      m[i][0] = i;
    for(int j = 1; j <= l2.size(); j++)
      m[0][j] = j;

    for(int i = 1; i <= l1.size(); i++) {
      for(int j = 1; j <= l2.size(); j++) {
        m[i][j] = Math.min(m[i-1][j-1] + ((l1.get(i-1).equals(l2.get(j-1))) ? 0 : 1), m[i-1][j] + 1);
        m[i][j] = Math.min(m[i][j], m[i][j-1] + 1);
      }
    }
    return m[l1.size()][l2.size()];
  }

  public static boolean isInteger(String str) {
    try {
      Integer.parseInt(str);
      return true;
    }
    catch(NumberFormatException e) {
      return false;
    }
  }
  
  /**
   * Map with WordNet derivations
   */
  public static Map<String, Set<String>> loadDerivations(String path) {
    Map<String,Set<String>> res = new HashMap<>();
    for(String line: IOUtils.readLines(path)) {
      String[] tokens = line.split("\t");
      MapUtils.addToSet(res, tokens[0], tokens[2]);
    }
    LogInfo.logs("TransUtis.loadDerivations: number of entries=%s",res.size());
    return res;
  }
  
  /**
   * Match a sublist in a list
   */
  public static boolean matchLists(List<String> list, List<String> pattern) {
    for(int i = 0; i < list.size(); ++i) {
      if(matchListsFromIndex(list,pattern,i))
        return true;
    }
    return false;
  }

  private static boolean matchListsFromIndex(List<String> list, List<String> pattern, int start) {

    if(start+pattern.size()>list.size())
      return false;
    for(int j = 0; j < pattern.size();++j) {
      if(!pattern.get(j).equals(list.get(start+j)))
        return false;
    }
    return true;
  }
  
  /**
   * Deletes spans that are contained in another span
   */
  public static Set<IntPair> getMaxNonOverlappingSpans(Set<IntPair> entitySpans) {
    Set<IntPair> res = new HashSet<>();
    for(IntPair pair1: entitySpans) {
      boolean containedSpan=false;
      for(IntPair pair2: entitySpans) {
        if(containedSpan(pair1,pair2)) {
          containedSpan=true;
          break;
        }
      }
      if(!containedSpan)
        res.add(pair1);
    }
    return res;
  }

  /**
   * Is pair1 contained in pair2
   */
  public static boolean containedSpan(IntPair pair1, IntPair pair2) {
    return (pair1.first >= pair2.first && pair1.second < pair2.second) ||
            (pair1.first > pair2.first && pair1.second <= pair2.second);
  }
  
  /**
   * does span1 intersect with span2
   */
  public static boolean intervalIntersect(IntPair span1, IntPair span2) {
    if(span1.first == span1.second || span2.first == span2.second) return false;
    if(span1.first>=span2.first &&
        span1.first<span2.second)
      return true;
    return span2.first >= span1.first &&
            span2.first < span1.second;

  }



}
