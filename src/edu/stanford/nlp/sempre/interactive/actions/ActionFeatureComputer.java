package edu.stanford.nlp.sempre.interactive.actions;

import fig.basic.*;
import java.util.*;

import com.beust.jcommander.internal.Lists;
import edu.stanford.nlp.sempre.*;

/**
 * Sida Wang
 * Feature computer for the BeamFloatingParser
 * TODOs:
 * - control what categories to abstract out
 * - efficiency improvement, right now use all members of the cross product
 */
public class ActionFeatureComputer implements FeatureComputer {
  public static class Options {
    @Option(gloss = "Verbosity")
    public int verbose = 0;

    @Option(gloss = "the N in N-gram")
    public int ngramN = 3;

    @Option(gloss = "size of the context window to consider")
    public int windowSize = 2;
  }
  public static Options opts = new Options();

  @Override public void extractLocal(Example ex, Derivation deriv) {
    addStatsFeatures(ex, deriv);
    addWindowFeatures(ex, deriv);
  }


  // function to abstract out ALL anchored stuff in the utterance.
  private List<String> abstractAnchors(Derivation deriv, List<String> tokens) {
    if (deriv.start == -1) 
      return tokens;
    List<String> newTokens = new ArrayList<>();
    int startInd = Math.max(0, deriv.start - opts.windowSize);
    int endInd = Math.min(tokens.size(), deriv.end + opts.windowSize);
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
      paddedTokens.addAll(tokens.subList(Math.max(0, deriv.start-n+1), Math.min(tokens.size(), deriv.end+n-1)));
    }

    for (int i=0; i<paddedTokens.size()-n+1; i++) {
      List<String> current = new ArrayList<>(paddedTokens.subList(i, i+n));
      ngrams.add( current.toString() );
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
      paddedTokens.addAll(tokens.subList(Math.max(0, deriv.start-2), Math.min(tokens.size(), deriv.end+2)));

    for (int i=0; i<tokens.size()-2; i++) {
      ngrams.add( "[" + tokens.get(i).toString() + ", *, " + tokens.get(i+2) +"]");
    }
    return ngrams;
  }
  
  private void addWindowFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("window")) return;
    if (deriv.rule != Rule.nullRule) {
      List<String> abstractAnchors = abstractAnchors(deriv, ex.getTokens());
      deriv.addFeature("win", abstractAnchors.toString());
    }
  }
  
  private void addStatsFeatures(Example ex, Derivation deriv) {
    if (!FeatureExtractor.containsDomain("stats")) return;
    if (deriv.rule != Rule.nullRule) {
      String cat = deriv.rule.getLhs();
      if (deriv.rule.isAnchored())
        deriv.addFeature("stats", "numAnchored");
      // deriv.addFeature("stats", cat);
      deriv.addFeature("stats", "anchored-" + cat + "-" + deriv.rule.isAnchored());
      deriv.addFeature("stats", "depth");
      
      if (deriv.rule.getInfoTag("induced") == 1.0)
        deriv.addFeature("stats", "numInduced");
      else
        deriv.addFeature("stats", "numCore");
    }
  }
}
