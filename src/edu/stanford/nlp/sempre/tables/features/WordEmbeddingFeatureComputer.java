package edu.stanford.nlp.sempre.tables.features;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.sempre.*;
import fig.basic.*;

/**
 * Compute vector space paraphrase features as in the paraphrase paper (Berant and Liang, 2014).
 *
 * The features are computed on all (phrase n-gram, predicate n-gram) pairs.
 *
 * @author ppasupat
 */
public class WordEmbeddingFeatureComputer implements FeatureComputer {
  public static class Options {
    @Option(gloss = "Verbosity")
    public int verbosity = 0;
    @Option(gloss = "Path to file containing word vectors, one per line")
    public String wordVectorFile;
    @Option(gloss = "Vector dimension")
    public int vecCapacity = 50;
  }
  public static Options opts = new Options();

  private Map<String, double[]> wordVectors;

  public WordEmbeddingFeatureComputer() {
    wordVectors = new HashMap<>();
    LogInfo.begin_track("Loading word embedding files ...");
    try (BufferedReader reader = IOUtils.openInHard(opts.wordVectorFile)) {
      String header = null, line;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split("\\s+");
        // Some word embedding files have a header which includes the number of
        // words and the number of dimensions.  Ignore this.
        if (header == null && tokens.length == 2) {
          header = line;
          continue;
        }
        if (tokens.length - 1 != opts.vecCapacity)
          throw new RuntimeException("Expected " + opts.vecCapacity + " tokens, but got " + (tokens.length - 1) + ": " + line);
        double[] vector = new double[opts.vecCapacity];
        for (int i = 1; i < tokens.length; ++i)
          vector[i - 1] = Double.parseDouble(tokens[i]);
        wordVectors.put(tokens[0], vector);
      }
    } catch (IOException e) {
      e.printStackTrace();
      LogInfo.fail(e);
    }
    LogInfo.logs("%d words loaded (%d dimensions)", wordVectors.size(), opts.vecCapacity);
    LogInfo.end_track();
  }
  
  @Override public void setExecutor(Executor executor) { }    // Do nothing

  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    if (!(FeatureExtractor.containsDomain("word-embedding"))) return;
    if (!deriv.isRoot(ex.numTokens())) return;
    // Utterance
    double[] utteranceVector = new double[opts.vecCapacity];
    int numUtteranceTokens = 0;
    for (String token : ex.languageInfo.tokens) {
      double[] tokenVector = wordVectors.get(token);
      if (tokenVector == null) {
        if (opts.verbosity >= 2) LogInfo.logs("Utterance word not found: %s", token);
        continue;
      }
      ListUtils.addMut(utteranceVector, tokenVector);
      numUtteranceTokens++;
    }
    if (numUtteranceTokens == 0) return;
    ListUtils.multMut(utteranceVector, 1.0 / numUtteranceTokens);
    // Predicates
    double[] predicateVector = new double[opts.vecCapacity];
    int numPredicateTokens = 0;
    for (PredicateInfo predicateInfo : PredicateInfo.getPredicateInfos(ex, deriv)) {
      if (predicateInfo.originalString == null) continue;
      String s = predicateInfo.originalString.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
      if (opts.verbosity >= 2) LogInfo.logs("|%s| ==> |%s|", predicateInfo.originalString, s);
      if (s.isEmpty()) continue;
      for (String token : s.split(" ")) {
        double[] tokenVector = wordVectors.get(token);
        if (tokenVector == null) {
          if (opts.verbosity >= 2) LogInfo.logs("Predicate word not found: %s", token);
          continue;
        }
        ListUtils.addMut(predicateVector, tokenVector);
        numPredicateTokens++;
      }
    }
    if (numPredicateTokens == 0) return;
    ListUtils.multMut(predicateVector, 1.0 / numPredicateTokens);
    // Outer product
    for (int i = 0; i < opts.vecCapacity; i++) {
      for (int j = 0; j < opts.vecCapacity; j++) {
        deriv.addFeature("w-e", "" + i + "," + j, utteranceVector[i] * predicateVector[j]);
      }
    }
  }

}