package edu.stanford.nlp.sempre;

import java.util.*;

import fig.basic.IntPair;

/**
 * Extract features specific to the floating parser.
 *
 * @author ppasupat
 */
public class FloatingFeatureComputer implements FeatureComputer {

  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    extractFloatingRuleFeatures(ex, deriv);
    extractFloatingSkipFeatures(ex, deriv);
  }

  // Conjunction of the rule and each lemma in the sentence
  void extractFloatingRuleFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("floatRule")) return;
    for (String lemma : ex.getLemmaTokens())
      deriv.addFeature("floatRule", "lemma=" + lemma + ",rule=" + deriv.rule.toString());
  }

  // Look for words with no anchored rule applied
  void extractFloatingSkipFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("floatSkip")) return;
    if (!deriv.isRoot(ex.numTokens())) return;
    // Get all anchored tokens
    boolean[] anchored = new boolean[ex.numTokens()];
    List<Derivation> stack = new ArrayList<>();
    stack.add(deriv);
    while (!stack.isEmpty()) {
      Derivation currentDeriv = stack.remove(stack.size() - 1);
      if (deriv.start != -1) {
        for (int i = deriv.start; i < deriv.end; i++)
          anchored[i] = true;
      } else {
        for (Derivation child : currentDeriv.children) {
          stack.add(child);
        }
      }
    }
    // Fire features based on tokens that are (not) anchored
    // See if named entities are skipped
    for (IntPair pair : ex.languageInfo.getNamedEntitySpans()) {
      for (int i = pair.first; i < pair.second; i++) {
        if (!anchored[i]) {
          String nerTag = ex.languageInfo.nerTags.get(i);
          deriv.addFeature("floatSkip", "skipped-ner=" + nerTag);
          break;
        }
      }
    }
    // See which POS tags are skipped
    for (int i = 0; i < anchored.length; i++) {
      if (!anchored[i]) {
        deriv.addFeature("floatSkip", "skipped-pos=" + ex.posTag(i));
      }
    }
  }

}
