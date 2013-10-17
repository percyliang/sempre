package edu.stanford.nlp.sempre.fbalignment.lexicons;

import com.google.common.base.Strings;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sempre.fbalignment.utils.MathUtils;
import fig.basic.Option;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * We define similarity to be positive
 *
 * @author jonathanberant
 */
public class WordDistance {

  private Map<String, List<Double>> wordVectors;

  public static class Options {
    @Option(gloss = "Path to file containing word vectors, one per line") public String wordVectorFile;
    @Option(gloss = "Method for calculuating distances") public String distanceMethod = "cosine";
  }

  public static Options opts = new Options();

  private static WordDistance wordDistance;
  public static WordDistance getSingleton() {
    if (wordDistance == null) wordDistance = new WordDistance();
    return wordDistance;
  }

  private WordDistance() {
    wordVectors = new HashMap<String, List<Double>>();

    if (Strings.isNullOrEmpty(opts.wordVectorFile))
      return;

    for (String line : IOUtils.readLines(opts.wordVectorFile)) {
      String[] tokens = line.split("\\s+");
      List<Double> vector = new ArrayList<Double>();
      for (int i = 1; i < tokens.length; ++i)
        vector.add(Double.parseDouble(tokens[i]));
      wordVectors.put(tokens[0], vector);
    }
  }

  public double score(String word1, String word2) {
    if (!wordVectors.containsKey(word1) || !wordVectors.containsKey(word2))
      return noVectorScore();
    if (opts.distanceMethod.equals("cosine")) {
      return Math.max(MathUtils.vectorCosine(wordVectors.get(word1), wordVectors.get(word2)), 0);
    }
    if (opts.distanceMethod.equals("euclid")) {
      return MathUtils.euclidDistance(wordVectors.get(word1), wordVectors.get(word2));
    }
    throw new RuntimeException("Unknown similarity method: " + opts.distanceMethod);
  }

  private double noVectorScore() {
    if (opts.distanceMethod.equals("cosine"))
      return 0.0;
    if (opts.distanceMethod.equals("euclid"))
      return 10.0;
    throw new RuntimeException("Unknown similarity method: " + opts.distanceMethod);
  }

  public static ExtremeValueWrapper buildDistanceWrapper() {
    if (opts.distanceMethod.equals("cosine"))
      return new MaxValueWrapper(0);
    if (opts.distanceMethod.equals("euclid"))
      return new MinValueWrapper(10);
    throw new RuntimeException("Unknown similarity method: " + opts.distanceMethod);
  }
}
