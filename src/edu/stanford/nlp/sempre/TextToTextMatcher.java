package edu.stanford.nlp.sempre;

import edu.stanford.nlp.sempre.fbalignment.lexicons.TokenLevelMatchFeatures;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.StringUtils;
import fig.basic.LogInfo;
import fig.basic.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Computes various text-text-similarities
 *
 * @author jonathanberant
 */
public class TextToTextMatcher {

  public static class Options {
    @Option(gloss = "Verbose") public int verbose = 0;
  }

  public static Options opts = new Options();

  private static TextToTextMatcher textToTextMatcher;
  public static TextToTextMatcher getSingleton() {
    if (textToTextMatcher == null) textToTextMatcher = new TextToTextMatcher();
    return textToTextMatcher;
  }
  //private WordDistance wordDistance;
  private Stemmer stemmer;

  public TextToTextMatcher() {
    //wordDistance = WordDistance.getSingleton();
    stemmer = new Stemmer();
  }

  public FeatureVector extractFeatures(List<String> exampleTokens, List<String> examplePosTags, List<String> exampleLemmas, Set<String> fbDescs) {
    FeatureVector res = new FeatureVector();
    extractTokenMatchFeatures(exampleTokens, exampleLemmas, fbDescs, res);
    extractWordSimilarityFeatures(exampleTokens, examplePosTags, fbDescs, res);
    return res;
  }

  private void extractWordSimilarityFeatures(List<String> exampleTokens, List<String> examplePosTags, Set<String> fbDescs, FeatureVector vector) {
    if (!FeatureExtractor.containsDomain("wordSim")) return;

    int minEditDistance = Integer.MAX_VALUE;
    //ExtremeValueWrapper simWrapper = WordDistance.buildDistanceWrapper();

    //String minWordDistFbToken = "", minWordDistTextToken = "";
    String minEditDistFbToken = "", minEditDistTextToken = "";
    //String similarityPos = "";

    for (String fbDesc : fbDescs) {
      List<String> fbDescTokens = FbFormulasInfo.BinaryFormulaInfo.tokenizeFbDescription(fbDesc);
      for (String fbDescToken : fbDescTokens) {
        if (fbDescToken.length() <= 2)
          continue;

        for (int i = 0; i < exampleTokens.size(); ++i) {

          String pos = examplePosTags.get(i);
          String textToken = exampleTokens.get(i);
          if (pos.startsWith("NN") || (pos.startsWith("VB") && !pos.equals("VBD-AUX")) || pos.equals("JJ")) {

            int currEditDist = StringUtils.editDistance(textToken, fbDescToken);
            //double currWordDist = wordDistance.score(textToken, fbDescToken);

            if (currEditDist < minEditDistance) {
              minEditDistance = currEditDist;
              minEditDistFbToken = fbDescToken;
              minEditDistTextToken = textToken;
            }
//            if (simWrapper.add(currWordDist)) {
//              minWordDistFbToken = fbDescToken;
//              minWordDistTextToken = textToken;
//              similarityPos = examplePosTags.get(i).substring(0, 1);
//            }
          }

        }
      }
    }
    minEditDistance = Math.min(minEditDistance, 15);
    if (opts.verbose >= 3) {
      LogInfo.logs("Min edit distance [%s,%s]: %s", minEditDistFbToken, minEditDistTextToken, minEditDistance);
      //LogInfo.logs("Extreme similarity [%s,%s]: %s", minWordDistFbToken, minWordDistTextToken, simWrapper.distance);
    }
    vector.addWithBias("wordSim", "binary_edit_distance", minEditDistance);
//    if (!similarityPos.equals(""))
//      vector.addWithBias("wordSim", "binary_word_sim_" + similarityPos, simWrapper.distance);
  }

  public boolean existsTokenMatch(List<String> exampleTokens, List<String> exampleLemmas, Set<String> fbDescs) {
    //generate stems
    List<String> exampleStems = new ArrayList<String>();
    for (String token : exampleTokens)
      exampleStems.add(stemmer.stem(token));

    Counter<String> tokenFeatures = new ClassicCounter<String>();
    Counter<String> stemFeatures = new ClassicCounter<String>();
    for (String fbDescription : fbDescs) {
      List<String> fbDescTokens = FbFormulasInfo.BinaryFormulaInfo.tokenizeFbDescription(fbDescription);
      List<String> fbDescStems = new ArrayList<String>();
      for (String fbDescToken : fbDescTokens)
        fbDescStems.add(stemmer.stem(fbDescToken));

      Counters.maxInPlace(tokenFeatures, TokenLevelMatchFeatures.extractTokenMatchFeatures(exampleTokens, fbDescTokens, true));
      Counters.maxInPlace(tokenFeatures, TokenLevelMatchFeatures.extractTokenMatchFeatures(exampleLemmas, fbDescTokens, true));
      Counters.maxInPlace(stemFeatures, TokenLevelMatchFeatures.extractTokenMatchFeatures(exampleStems, fbDescStems, false));
      if (tokenFeatures.size() > 0 || stemFeatures.size() > 0)
        return true;
    }
    return false;
  }

  private void extractTokenMatchFeatures(List<String> exampleTokens, List<String> exampleLemmas, Set<String> fbDescs, FeatureVector vector) {
    if (!FeatureExtractor.containsDomain("tokenMatch")) return;

    //generate stems
    List<String> exampleStems = new ArrayList<String>();
    for (String token : exampleTokens)
      exampleStems.add(stemmer.stem(token));

    Counter<String> tokenFeatures = new ClassicCounter<String>();
    Counter<String> stemFeatures = new ClassicCounter<String>();
    for (String fbDescription : fbDescs) {
      List<String> fbDescTokens = FbFormulasInfo.BinaryFormulaInfo.tokenizeFbDescription(fbDescription);
      List<String> fbDescStems = new ArrayList<String>();
      for (String fbDescToken : fbDescTokens)
        fbDescStems.add(stemmer.stem(fbDescToken));

      Counters.maxInPlace(tokenFeatures, TokenLevelMatchFeatures.extractTokenMatchFeatures(exampleTokens, fbDescTokens, true));
      Counters.maxInPlace(tokenFeatures, TokenLevelMatchFeatures.extractTokenMatchFeatures(exampleLemmas, fbDescTokens, true));
      Counters.maxInPlace(stemFeatures, TokenLevelMatchFeatures.extractTokenMatchFeatures(exampleStems, fbDescStems, false));
    }
    if (opts.verbose >= 3) {
      LogInfo.logs("Binary formula desc: %s, token match: %s, stem match: %s", fbDescs, tokenFeatures, stemFeatures);
    }
    addFeaturesToVector(tokenFeatures, "binary_token", vector);
    addFeaturesToVector(stemFeatures, "binary_stem", vector);
  }

  private void addFeaturesToVector(Counter<String> features, String prefix, FeatureVector vector) {
    if (features.getCount("equal") > 0)
      vector.add("tokenMatch", prefix + ".equal", features.getCount("equal"));
    else if (features.getCount("prefix") > 0 || features.getCount("suffix") > 0) {
      if (features.getCount("prefix") > 0)
        vector.add("tokenMatch", prefix + ".prefix", features.getCount("prefix"));
      if (features.getCount("suffix") > 0)
        vector.add("tokenMatch", prefix + ".suffix", features.getCount("suffix"));
    } else if (features.getCount("overlap") > 0)
      vector.add("tokenMatch", prefix + ".overlap", features.getCount("overlap"));
  }
}
