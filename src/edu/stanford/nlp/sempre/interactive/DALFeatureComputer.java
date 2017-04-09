package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.FeatureComputer;
import edu.stanford.nlp.sempre.FeatureExtractor;
import edu.stanford.nlp.sempre.Rule;
import fig.basic.Option;

/**
 * Feature computer for the the dependency-based action language TODOs: -
 * control what categories to abstract out - efficiency improvement, right now
 * use all members of the cross product
 * 
 * @author sidaw
 */
public class DALFeatureComputer implements FeatureComputer {
  public static class Options {
    @Option(gloss = "Verbosity")
    public int verbose = 0;

    @Option(gloss = "the N in N-gram")
    public int ngramN = 3;

    @Option(gloss = "size of the context window to consider")
    public int windowSize = 2;
  }

  public static Options opts = new Options();

  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    addStatsFeatures(ex, deriv);
    addWindowFeatures(ex, deriv);
    addSocialFeatures(ex, deriv);
    extractRuleFeatures(ex, deriv);
    extractSpanFeatures(ex, deriv);
  }

  // function to abstract out ALL anchored stuff in the utterance.
  private List<String> abstractAnchors(Derivation deriv, List<String> tokens, int window) {
    if (deriv.start == -1)
      return tokens;
    List<String> newTokens = new ArrayList<>();
    int startInd = Math.max(0, deriv.start - window);
    int endInd = Math.min(tokens.size(), deriv.end + window);
    newTokens.addAll(tokens.subList(startInd, deriv.start));
    newTokens.add(deriv.cat);
    newTokens.addAll(tokens.subList(deriv.end, endInd));
    return newTokens;
  }

  private List<String> getAllNgrams(List<String> tokens, int n, Derivation deriv) {
    List<String> ngrams = new ArrayList<>();
    List<String> paddedTokens = new ArrayList<>();
    if (deriv.start == -1) // floating, just add everything
      paddedTokens.addAll(tokens);
    else {
      paddedTokens.addAll(tokens.subList(Math.max(0, deriv.start - n + 1), Math.min(tokens.size(), deriv.end + n - 1)));
    }

    for (int i = 0; i < paddedTokens.size() - n + 1; i++) {
      List<String> current = new ArrayList<>(paddedTokens.subList(i, i + n));
      ngrams.add(current.toString());
    }
    return ngrams;
  }

  private List<String> getAllSkipGrams(List<String> tokens, Derivation deriv) {
    List<String> ngrams = new ArrayList<>();
    List<String> paddedTokens = new ArrayList<>();
    if (tokens.size() < 3)
      return ngrams;

    if (deriv.start == -1) // floating, just add everything
      paddedTokens.addAll(tokens);
    else
      paddedTokens.addAll(tokens.subList(Math.max(0, deriv.start - 2), Math.min(tokens.size(), deriv.end + 2)));

    for (int i = 0; i < tokens.size() - 2; i++) {
      ngrams.add("[" + tokens.get(i).toString() + ", *, " + tokens.get(i + 2) + "]");
    }
    return ngrams;
  }

  private void addWindowFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain(":window"))
      return;
    if (deriv.rule != Rule.nullRule) {
      deriv.addFeature(":window", abstractAnchors(deriv, ex.getTokens(), 1).toString());
      deriv.addFeature(":window", abstractAnchors(deriv, ex.getTokens(), 2).toString());
    }
  }

  private void addStatsFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain(":stats"))
      return;
    if (deriv.rule != Rule.nullRule) {
      if (deriv.rule.isInduced())
        deriv.addFeature(":stats", "induced");
      else
        deriv.addFeature(":stats", "core");

      if (deriv.rule.source != null) {
        deriv.addFeature(":stats", "cite", deriv.rule.source.cite);
        if (deriv.rule.source.cite > 0)
          deriv.addFeature(":stats", "has_cite");
        else
          deriv.addFeature(":stats", "no_cite");

        if (deriv.rule.source.self > 0)
          deriv.addFeature(":stats", "has_selfcite");
        else
          deriv.addFeature(":stats", "no_selfcite");

        if (deriv.rule.source.align)
          deriv.addFeature(":stats", "align");
        else
          deriv.addFeature(":stats", "no_align");

        if (deriv.rule.getInfoTag("simple_packing") != -1.0)
          deriv.addFeature(":stats", "simple_packing");
        else
          deriv.addFeature(":stats", "no_simple_packing");

      }
    }
  }

  private void addSocialFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain(":social"))
      return;
    if (deriv.rule != Rule.nullRule && deriv.rule.source != null) {
      // everyone like a particular author
      deriv.addFeature(":social", deriv.rule.source.uid);
      // a particular user likes a particular author, perhaps himself
      deriv.addFeature(":social", deriv.rule.source.uid + "::" + ex.id);
      // the degree everyone likes to use their own rules
      deriv.addFeature(":social", "isself::" + deriv.rule.source.uid.equals(ex.id));
    }
  }

  // Add an indicator for each applied rule.
  private void extractRuleFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain(":rule"))
      return;
    if (deriv.rule != Rule.nullRule) {
      deriv.addFeature(":rule", "fire");
      deriv.addFeature(":rule", deriv.rule.toString());
    }
  }

  // Extract features on the linguistic information of the spanned (anchored)
  // tokens.
  // (Not applicable for floating rules)
  private void extractSpanFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain(":span") || deriv.start == -1)
      return;
    deriv.addFeature(":span", "cat=" + deriv.cat + ":: len=" + (deriv.end - deriv.start));
    deriv.addFeature(":span", "cat=" + deriv.cat + ":: " + ex.token(deriv.start) + "..." + ex.token(deriv.end - 1));
  }

}
