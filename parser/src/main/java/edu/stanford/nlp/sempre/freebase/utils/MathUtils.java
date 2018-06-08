package edu.stanford.nlp.sempre.freebase.utils;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MathUtils {
  private MathUtils() { }

  public static double jaccard(double intersection, double size1, double size2, double smoothing) {
    return intersection / (size1 + size2 + smoothing - intersection);
  }

  public static <E> double generalizedJensenShannonDivergence(Counter<E> c1, Counter<E> c2) {

    double sum = 0.0;
    Set<E> nonZeroEntries = new HashSet<E>();
    for (E entry : nonZeroEntries) {
      double u = c1.getCount(entry);
      double v = c2.getCount(entry);
      sum += coordinateJsDivergence(u, v);
    }
    return sum / 2.0;
  }

  private static double coordinateJsDivergence(double u, double v) {
    return u * Math.log(2 * u / (u + v)) + v * Math.log(2 * v / (u + v));
  }

  public static double coordinateJsDiverDeriv(double x, double y) {
    if (x == 0.0)
      return 0.0;
    if (y == 0.0)
      return Math.log(2) / 2.0;

    double xPlusY = x + y;
    double res = -1 * Math.log(xPlusY) - (y / xPlusY) + x * (1 / x - 1 / xPlusY) + Math.log(x) + Math.log(2);
    return res / 2.0;
  }

  public static Counter<String> prefixCounterKeys(Counter<String> counter, String prefix) {
    Counter<String> res = new ClassicCounter<String>();
    for (String key : counter.keySet()) {
      res.setCount(prefix + "_" + key, counter.getCount(key));
    }
    return res;
  }

  public static double vectorCosine(List<Double> array1, List<Double> array2) {

    if (array1.size() != array2.size())
      throw new RuntimeException("Cannot compute cosine of arrays of differnt sizes: " + array1.size() + " " + array2.size());
    double dotProd = 0.0;
    double lsq1 = 0.0;
    double lsq2 = 0.0;

    for (int i = 0; i < array1.size(); ++i) {
      dotProd += array1.get(i) * array2.get(i);
      lsq1 += array1.get(i) * array1.get(i);
      lsq2 += array2.get(i) * array2.get(i);
    }
    return dotProd / (Math.sqrt(lsq1) * Math.sqrt(lsq2));
  }

  public static double euclidDistance(List<Double> array1, List<Double> array2) {

    if (array1.size() != array2.size())
      throw new RuntimeException("Cannot compute cosine of arrays of differnt sizes: " + array1.size() + " " + array2.size());

    double sqDistance = 0.0;
    for (int i = 0; i < array1.size(); ++i) {
      sqDistance += Math.pow(array1.get(i) - array2.get(i), 2);
    }
    return Math.sqrt(sqDistance);
  }

  public static <T> double sumDoubleMap(Map<T, DoubleContainer> map) {
    double sum = 0.0;
    for (DoubleContainer d : map.values())
      sum += d.value();
    return sum;
  }

  public static <T> void normalizeDoubleMap(Map<T, DoubleContainer> map) {
    double sum = 0.0;
    for (DoubleContainer d : map.values())
      sum += d.value();
    for (T key : map.keySet()) {
      double normalizedValue = map.get(key).value() / sum;
      map.get(key).set(normalizedValue);
    }
  }

  /**
   * Computes jaccard between sets of objects
   * @param x
   * @param y
   * @return
   */
  public static <T> double jaccard(Set<T> x, Set<T> y) {

    Set<T> intersection = new HashSet<T>(x);
    intersection.retainAll(y);
    Set<T> union = new HashSet<T>(x);
    union.addAll(y);

    double res = union.size() == 0 ? 1.0 : (double) intersection.size() / union.size();
    return res;
  }

  /**
   * Computes jaccard between sets of objects
   * @param x
   * @param y
   * @return
   */
  public static <T> double jaccard(List<T> x, List<T> y) {

    Set<T> intersection = new HashSet<T>(x);
    intersection.retainAll(y);
    Set<T> union = new HashSet<T>(x);
    union.addAll(y);

    double res = union.size() == 0 ? 1.0 : (double) intersection.size() / union.size();
    return res;
  }

  /**
   * how many of the tokens in x are covered by y
   * @param x
   * @param y
   * @return
   */
  public static <T> double coverage(List<T> x, List<T> y) {
    Set<T> yTokens = new HashSet<T>(y);
    int covered = 0;
    for (T xItem : x) {
      if (yTokens.contains(xItem))
        covered++;
    }
    return (double) covered / x.size();
  }

  /**
   * Geometric average of unigram bigram and trigram precision
   * @param test
   * @param ref
   * @return
   */

  public static double bleu(List<String> test, List<String> ref) {

    Set<String> refUnigrams = new HashSet<String>();
    Set<String> refBigrams = new HashSet<String>();
    Set<String> refTrigrams = new HashSet<String>();
    for (int i = 0; i < ref.size(); ++i) {
      refUnigrams.add(ref.get(i));
      if (i < ref.size() - 1)
        refBigrams.add(ref.get(i) + " " + ref.get(i + 1));
      if (i < ref.size() - 2)
        refTrigrams.add(ref.get(i) + " " + ref.get(i + 1) + " " + ref.get(i + 2));
    }
    int unigramCov = 0;
    int bigramCov = 0;
    int trigramCov = 0;
    for (int i = 0; i < test.size(); ++i) {
      if (refUnigrams.contains(test.get(i)))
        unigramCov++;
      if (i < test.size() - 1) {
        String bigram = test.get(i) + " " + test.get(i + 1);
        if (refBigrams.contains(bigram))
          bigramCov++;
      }
      if (i < test.size() - 2) {
        String trigram = test.get(i) + " " + test.get(i + 1) + " " + test.get(i + 2);
        if (refTrigrams.contains(trigram))
          trigramCov++;
      }
    }
    double unigramPrec = (double) unigramCov / test.size();
    double bigramPrec = (double) bigramCov / (test.size() - 1);
    double trigramPrec = (double) trigramCov / (test.size() - 2);
    double exponent = (double) 1 / 3;
    return Math.pow(unigramPrec * bigramPrec * trigramPrec, exponent);
  }

  public static double tokensCosine(List<String> x, List<String> y) {

    Counter<String> xCounter = new ClassicCounter<String>();
    for (String str : x)
      xCounter.incrementCount(str);
    Counter<String> yCounter = new ClassicCounter<String>();
    for (String str : y)
      yCounter.incrementCount(str);
    return Counters.cosine(xCounter, yCounter);
  }
}
