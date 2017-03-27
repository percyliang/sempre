package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import fig.basic.*;

import java.util.*;

/**
 * A FeatureVector represents a mapping from feature (string) to value
 * (double).
 *
 * We enforce the convention that each feature is (domain, name),
 * so that the key space isn't a free-for-all.
 *
 * @author Percy Liang
 * @author Jonathan Berant
 */
public class FeatureVector {
  public static class Options {
    @Option(gloss = "When logging, ignore features with zero weight")
    public boolean ignoreZeroWeight = false;
    @Option(gloss = "Log only this number of top and bottom features")
    public int logFeaturesLimit = Integer.MAX_VALUE;
  }
  public static Options opts = new Options();

  // These features map to the value 1 (most common case in NLP).
  private ArrayList<String> indicatorFeatures;
  // General features
  private ArrayList<Pair<String, Double>> generalFeatures;
  // A dense array of features to save memory
  private double[] denseFeatures;
  private static final String DENSE_NAME = "Dns";

  public FeatureVector() { } // constructor that does nothing

  public FeatureVector(int numOfDenseFeatures) {
    denseFeatures = new double[numOfDenseFeatures];
    Arrays.fill(denseFeatures, 0d);
  }

  private static String toFeature(String domain, String name) { return domain + " :: " + name; }

  public void add(String domain, String name) {
    add(toFeature(domain, name));
  }
  private void add(String feature) {
    if (indicatorFeatures == null) indicatorFeatures = new ArrayList<>();
    indicatorFeatures.add(feature);
  }

  public void add(String domain, String name, double value) {
    add(toFeature(domain, name), value);
  }
  private void add(String feature, double value) {
    if (generalFeatures == null) generalFeatures = new ArrayList<>();
    generalFeatures.add(Pair.newPair(feature, value));
  }

  public void addWithBias(String domain, String name, double value) {
    add(domain, name, value);
    add(domain, name + "-bias", 1);
  }

  // Add histogram features, e.g., domain :: name>=4
  public void addHistogram(String domain, String name, double value) { addHistogram(domain, name, value, 2, 10, true); }
  public void addHistogram(String domain, String name, double value, int initBinSize, int numBins, boolean exp) {
    double upper = initBinSize;
    String bin = null;
    int sign = value > 0 ? +1 : -1;
    value = Math.abs(value);
    for (int i = 0; i < numBins; i++) {
      double lastUpper = upper;
      if (i > 0) {
        if (exp) upper *= initBinSize;
        else upper += initBinSize;
      }
      if (value < upper) {
        bin = (sign > 0) ? lastUpper + ":" + upper : (-upper) + ":" + (-lastUpper);
        break;
      }
    }
    if (bin == null)
      bin = (sign > 0) ? ">=" + upper : "<=" + (-upper);

    add(domain, name + bin);
  }

  public void addFromString(String feature, double value) {
    assert feature.contains(" :: ") : feature;
    if (value == 1) add(feature);
    else add(feature, value);
  }

  public void addDenseFeature(int index, double value) {
    denseFeatures[index] += value;
  }

  public void add(FeatureVector that) { add(that, AllFeatureMatcher.matcher); }
  public void add(double scale, FeatureVector that) { add(scale, that, AllFeatureMatcher.matcher); }
  public void add(FeatureVector that, FeatureMatcher matcher) { add(1, that, matcher); }
  public void add(double scale, FeatureVector that, FeatureMatcher matcher) {
    if (that.indicatorFeatures != null) {
      for (String f : that.indicatorFeatures)
        if (matcher.matches(f)) {
          if (scale == 1)
            add(f);
          else
            add(f, scale);
        }
    }
    if (that.generalFeatures != null) {
      for (Pair<String, Double> pair : that.generalFeatures)
        if (matcher.matches(pair.getFirst()))
          add(pair.getFirst(), scale * pair.getSecond());
    }
    // dense features are always added
    if (that.denseFeatures != null) {
      for (int i = 0; i < denseFeatures.length; ++i)
        denseFeatures[i] += scale * that.denseFeatures[i];
    }
  }

  // Return the dot product between this feature vector and the weight vector (parameters).
  public double dotProduct(Params params) {
    double sum = 0;
    if (indicatorFeatures != null) {
      for (String f : indicatorFeatures)
        sum += params.getWeight(f);
    }
    if (generalFeatures != null) {
      for (Pair<String, Double> pair : generalFeatures)
        sum += params.getWeight(pair.getFirst()) * pair.getSecond();
    }
    if (denseFeatures != null) {
      for (int i = 0; i < denseFeatures.length; ++i)
        sum += params.getWeight(DENSE_NAME + "_" + i) * denseFeatures[i];
    }
    return sum;
  }

  // Increment |map| by a factor times this feature vector.
  // converts the dense features to a non-dense representation
  public void increment(double factor, Map<String, Double> map) {
    increment(factor, map, AllFeatureMatcher.matcher);
  }
  public void increment(double factor, Map<String, Double> map, FeatureMatcher matcher) {
    if (indicatorFeatures != null) {
      for (String feature : indicatorFeatures)
        if (matcher.matches(feature))
          MapUtils.incr(map, feature, factor);
    }
    if (generalFeatures != null) {
      for (Pair<String, Double> pair : generalFeatures)
        if (matcher.matches(pair.getFirst()))
          MapUtils.incr(map, pair.getFirst(), factor * pair.getSecond());
    }
    if (denseFeatures != null) {
      for (int i = 0; i < denseFeatures.length; ++i)
        MapUtils.incr(map, DENSE_NAME + "_" + i, factor * denseFeatures[i]);
    }
  }

  // returns a feature vector where all features are prefixed
  public FeatureVector addPrefix(String prefix) {
    FeatureVector res = new FeatureVector();
    if (indicatorFeatures != null) {
      for (String feature : indicatorFeatures)
        res.add(prefix + feature);
    }
    if (generalFeatures != null) {
      for (Pair<String, Double> pair : generalFeatures) {
        res.add(prefix + pair.getFirst(), pair.getSecond());
      }
    }
    return res;
  }

  @JsonValue
  public Map<String, Double> toMap() {
    HashMap<String, Double> map = new HashMap<String, Double>();
    increment(1, map);
    if (denseFeatures != null) {
      for (int i = 0; i < denseFeatures.length; ++i) {
        map.put(DENSE_NAME + "_" + i, denseFeatures[i]);
      }
    }
    return map;
  }

  @JsonCreator
  public static FeatureVector fromMap(Map<String, Double> m) {
    // TODO (rf):
    // Encoding is lossy.  We guess that value of 1 means indicator, but we
    // could be wrong.
    // TODO(joberant) - takes care of dense features in a non efficient way
    int maxDenseFeaturesIndex = -1;
    for (Map.Entry<String, Double> entry : m.entrySet()) {
      if (isDenseFeature(entry.getKey())) {
        int index = denseFeatureIndex(entry.getKey());
        if (index > maxDenseFeaturesIndex)
          maxDenseFeaturesIndex = index;
      }
    }

    FeatureVector fv = maxDenseFeaturesIndex == -1 ? new FeatureVector() : new FeatureVector(maxDenseFeaturesIndex + 1);
    for (Map.Entry<String, Double> entry : m.entrySet()) {
      if (isDenseFeature(entry.getKey())) {
        fv.addDenseFeature(denseFeatureIndex(entry.getKey()), entry.getValue());
      } else {
        if (entry.getValue() == 1.0d)
          fv.add(entry.getKey());
        else
          fv.add(entry.getKey(), entry.getValue());
      }
    }
    return fv;
  }

  private static boolean isDenseFeature(String f) {
    return f.startsWith(DENSE_NAME);
  }
  private static int denseFeatureIndex(String denseFeature) {
    assert denseFeature.startsWith(DENSE_NAME);
    return Integer.parseInt(denseFeature.split("_")[1]);
  }

  public static void logChoices(String prefix, Map<String, Integer> choices) {
    LogInfo.begin_track("%s choices", prefix);
    for (Map.Entry<String, Integer> e : choices.entrySet()) {
      int value = e.getValue();
      if (value == 0) continue;
      LogInfo.logs("%s %s", value > 0 ? "+" + value : value, e.getKey());
    }
    LogInfo.end_track();
  }

  public static void logFeatureWeights(String prefix, Map<String, Double> features, Params params) {
    List<Map.Entry<String, Double>> entries = new ArrayList<Map.Entry<String, Double>>();
    double sumValue = 0;
    for (Map.Entry<String, Double> entry : features.entrySet()) {
      String feature = entry.getKey();
      if (entry.getValue() == 0) continue;
      double value = entry.getValue() * params.getWeight(feature);
      if (opts.ignoreZeroWeight && value == 0) continue;
      sumValue += value;
      entries.add(new java.util.AbstractMap.SimpleEntry<String, Double>(feature, value));
    }
    Collections.sort(entries, new ValueComparator<String, Double>(false));
    LogInfo.begin_track_printAll("%s features [sum = %s] (format is feature value * weight)", prefix, Fmt.D(sumValue));
    if (entries.size() / 2 > opts.logFeaturesLimit) {
      for (Map.Entry<String, Double> entry : entries.subList(0, opts.logFeaturesLimit)) {
        String feature = entry.getKey();
        double value = entry.getValue();
        double weight = params.getWeight(feature);
        LogInfo.logs("%-50s %6s = %s * %s", "[ " + feature + " ]", Fmt.D(value), Fmt.D(MapUtils.getDouble(features, feature, 0)), Fmt.D(weight));
      }
      LogInfo.logs("... (%d more features) ...", entries.size() - 2 * opts.logFeaturesLimit);
      for (Map.Entry<String, Double> entry : entries.subList(entries.size() - opts.logFeaturesLimit, entries.size())) {
        String feature = entry.getKey();
        double value = entry.getValue();
        double weight = params.getWeight(feature);
        LogInfo.logs("%-50s %6s = %s * %s", "[ " + feature + " ]", Fmt.D(value), Fmt.D(MapUtils.getDouble(features, feature, 0)), Fmt.D(weight));
      }
    } else {
      for (Map.Entry<String, Double> entry : entries) {
        String feature = entry.getKey();
        double value = entry.getValue();
        double weight = params.getWeight(feature);
        LogInfo.logs("%-50s %6s = %s * %s", "[ " + feature + " ]", Fmt.D(value), Fmt.D(MapUtils.getDouble(features, feature, 0)), Fmt.D(weight));
      }
    }
    LogInfo.end_track();
  }

  public static void logFeatures(Map<String, Double> features) {
    for (String key : features.keySet()) {
      LogInfo.logs("%s\t%s", key, features.get(key));
    }
  }

  public void clear() {
    if (indicatorFeatures != null)
      indicatorFeatures.clear();
    if (generalFeatures != null)
      generalFeatures.clear();
    denseFeatures = null;
  }
}
