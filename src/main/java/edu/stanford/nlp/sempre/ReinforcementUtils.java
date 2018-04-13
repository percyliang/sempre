package edu.stanford.nlp.sempre;

import fig.basic.MapUtils;
import fig.basic.NumUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Utils for <code>ReinforcementParser</code>
 */
public final class ReinforcementUtils {
  private static double logMaxValue = Math.log(Double.MAX_VALUE);
  private ReinforcementUtils() { }

  // add to double map after adding prefix to all keys
  public static void addToDoubleMap(Map<String, Double> mutatedMap, Map<String, Double> addedMap, String prefix) {
    for (String key : addedMap.keySet())
      MapUtils.incr(mutatedMap, prefix + key, addedMap.get(key));
  }

  public static void subtractFromDoubleMap(Map<String, Double> mutatedMap, Map<String, Double> subtractedMap) {
    for (String key : subtractedMap.keySet())
      MapUtils.incr(mutatedMap, key, -1 * subtractedMap.get(key));
  }
  // subtract from double map after adding prefix to all keys
  public static void subtractFromDoubleMap(Map<String, Double> mutatedMap, Map<String, Double> subtractedMap, String prefix) {
    for (String key : subtractedMap.keySet())
      MapUtils.incr(mutatedMap, prefix + key, -1 * subtractedMap.get(key));
  }

  public static Map<String, Double> multiplyDoubleMap(Map<String, Double>  map, double factor) {
    Map<String, Double> res = new HashMap<>();
    for (Map.Entry<String, Double> entry: map.entrySet())
      res.put(entry.getKey(), entry.getValue() * factor);
    return res;
  }

  public static int sampleIndex(Random rand, List<? extends HasScore> scorables, double denominator) {
    double randD = rand.nextDouble();
    double sum = 0;

    for (int i = 0; i < scorables.size(); ++i) {
      HasScore pds = scorables.get(i);
      double prob = computeProb(pds, denominator);
      sum += prob;
      if (randD < sum) {
        return i;
      }
    }
    throw new RuntimeException(sum + " < " + randD);
  }

  public static int sampleIndex(Random rand, double[] scores, double denominator) {
    double randD = rand.nextDouble();
    double sum = 0;

    for (int i = 0; i < scores.length; ++i) {
      double pds = scores[i];
      double prob = computeProb(pds, denominator);
      sum += prob;
      if (randD < sum) {
        return i;
      }
    }
    throw new RuntimeException(sum + " < " + randD);
  }

  public static int sampleIndex(Random rand, double[] probs) {
    double randD = rand.nextDouble();
    double sum = 0;

    for (int i = 0; i < probs.length; ++i) {
      sum += probs[i];
      if (randD < sum) return i;
    }
    throw new RuntimeException(sum + " < " + randD);
  }

  public static double computeProb(HasScore deriv, double denominator) {
    double prob = Math.exp(deriv.getScore() - denominator);
    if (prob < -0.0001 || prob > 1.0001)
      throw new RuntimeException("Probability is out of range, prob=" + prob +
              ",score=" + deriv.getScore() + ", denom=" + denominator);
    return prob;
  }

  public static double computeProb(double score, double denominator) {
    double prob = Math.exp(score - denominator);
    if (prob < -0.0001 || prob > 1.0001)
      throw new RuntimeException("Probability is out of range, prob=" + prob +
              ",score=" + score + ", denom=" + denominator);
    return prob;
  }

  public static double computeLogExpSum(List<? extends HasScore> scorables) {
    double sum = Double.NEGATIVE_INFINITY;
    for (HasScore scorable : scorables) {
      sum = NumUtils.logAdd(sum, scorable.getScore());
    }
    return sum;
  }

  public static double[] expNormalize(List<? extends HasScore> scorables) {
    // Input: log probabilities (unnormalized too)
    // Output: normalized probabilities
    // probs actually contains log probabilities; so we can add an arbitrary constant to make
    // the largest log prob 0 to prevent overflow problems
    double[] res = new double[scorables.size()];
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < scorables.size(); i++)
      max = Math.max(max, scorables.get(i).getScore());
    if (Double.isInfinite(max))
      throw new RuntimeException("Scoreables is probably empty");
    for (int i = 0; i < scorables.size(); i++)
      res[i] = Math.exp(scorables.get(i).getScore() - max);
    NumUtils.normalize(res);
    return res;
  }

  public static double[] expNormalize(ParserAgenda<? extends HasScore> scorables) {
    // Input: log probabilities (unnormalized too)
    // Output: normalized probabilities
    // probs actually contains log probabilities; so we can add an arbitrary constant to make
    // the largest log prob 0 to prevent overflow problems
    double[] res = new double[scorables.size()];
    double max = Double.NEGATIVE_INFINITY;


    for (HasScore scorable : scorables)
      max = Math.max(max, scorable.getScore());

    if (Double.isInfinite(max))
      throw new RuntimeException("Scoreables is probably empty");

    int i = 0;
    for (HasScore scorable : scorables)
      res[i++] = Math.exp(scorable.getScore() - max);
    NumUtils.normalize(res);
    return res;
  }

  // Return log(exp(a)-exp(b))
  public static double logSub(double a, double b) {
    if(a <= b) throw new RuntimeException("First argument must be strictly greater than second argument");
    if(Double.isInfinite(b) || a-b > logMaxValue || b-a < 30) return a;
    return a + Math.log(1d - Math.exp(b-a));
  }
}
