package edu.stanford.nlp.sempre;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.ArrayUtils;

import java.util.Set;

/**
 * Extracts indicator features that count how many times semantic functions and
 * LHSs have been used in the derivation For now we count how many times MergeFn
 * and JoinFn, also how many time unary, binary and entity lexical entries have
 * been used. The feature is a pair with the operation and the count
 *
 * @author jonathanberant
 */
public class DerivOpCountFeatureExtractor {
  public static final String entityCat = "$Entity";
  public static final String unaryCat = "$Unary";
  public static final String binaryCat = "$Binary";
  public final static String joinFn = "JoinFn";
  public final static String mergeFn = "MergeFn";
  public final static String bridgeFn = "BridgeFn";
  public static Set<String> featureNames = ArrayUtils.asSet(new String[]{entityCat, unaryCat, binaryCat, joinFn, mergeFn, bridgeFn});

  public void extractLocal(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("opCount")) return;
    if (!deriv.isRoot(ex.numTokens())) return;

    //extract the operation count
    Counter<String> opCounter = new ClassicCounter<String>();
    extractOperationsRecurse(deriv, opCounter);
    addFeatures(deriv, opCounter);

    //add pre-terminal sequence
    //StringBuilder sb = new StringBuilder();
    //extractPreterminalYieldRecurse(deriv, sb);
    //deriv.localFeatureVector.add(sb.toString());
  }

  private void extractPreterminalYieldRecurse(Derivation deriv, StringBuilder sb) {
    //base case 1
    if (deriv.children.size() == 0)
      return;
    //base case 2
    if (deriv.rule.lhs.equals(entityCat) || deriv.rule.lhs.equals(unaryCat) || deriv.rule.lhs.equals(binaryCat)) {
      sb.append(deriv.rule.lhs + "_");
      return;
    }
    //recursive call from left to right
    for (Derivation child : deriv.children) {
      extractPreterminalYieldRecurse(child, sb);
    }
  }

  private void extractOperationsRecurse(Derivation deriv, Counter<String> opCounter) {

    //base case
    if (deriv.children.size() == 0)
      return;

    //boolean[] appendPreTerminal = new boolean[deriv.children.size()];

    //increment counts for current rule
    opCounter.incrementCount(deriv.rule.lhs);
    if (deriv.rule.sem instanceof JoinFn)
      opCounter.incrementCount(joinFn);
    else if (deriv.rule.sem instanceof MergeFn)
      opCounter.incrementCount(mergeFn);
    else if (deriv.rule.sem instanceof BridgeFn)
      opCounter.incrementCount(bridgeFn);
    //recursive call
    for (Derivation child : deriv.children)
      extractOperationsRecurse(child, opCounter);
  }

  private void addFeatures(Derivation deriv, Counter<String> opCounter) {
    for (String feature : featureNames)
      deriv.addFeature("opCount", "count(" + feature + ")=" + (int)opCounter.getCount(feature));
  }
}
