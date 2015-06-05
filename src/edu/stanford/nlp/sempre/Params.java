package edu.stanford.nlp.sempre;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import fig.basic.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Params contains the parameters of the model. Currently consists of a map from
 * features to weights.
 *
 * @author Percy Liang
 */
public class Params {
  public static class Options {
    @Option(gloss = "By default, all features have this weight")
    public double defaultWeight = 0;
    @Option(gloss = "Randomly initialize the weights")
    public boolean initWeightsRandomly = false;
    @Option(gloss = "Randomly initialize the weights")
    public Random initRandom = new Random(1);

    @Option(gloss = "Initial step size") public double initStepSize = 1;
    @Option(gloss = "How fast to reduce the step size")
    public double stepSizeReduction = 0;
    @Option(gloss = "Use the AdaGrad algorithm (different step size for each coordinate)")
    public boolean adaptiveStepSize = true;
    @Option(gloss = "Use dual averaging") public boolean dualAveraging = false;
    @Option(gloss = "Whether to do lazy l1 reg updates") public String l1Reg = "none";
    @Option(gloss = "L1 reg coefficient") public double l1RegCoeff = 0d;
    @Option(gloss = "Lazy L1 full update frequency") public int lazyL1FullUpdateFreq = 5000;
  }
  public static Options opts = new Options();
  public enum L1Reg {
    LAZY,
    NONLAZY,
    NONE;
  }
  private L1Reg parseReg(String l1Reg) {
    if ("lazy".equals(l1Reg)) return L1Reg.LAZY;
    if ("nonlazy".equals(l1Reg)) return L1Reg.NONLAZY;
    if ("none".equals(l1Reg)) return L1Reg.NONE;
    throw new RuntimeException("not legal l1reg");
  }
  private L1Reg l1Reg = parseReg(opts.l1Reg);

  // Discriminative weights
  private Map<String, Double> weights = new HashMap<>();

  // For AdaGrad
  Map<String, Double> sumSquaredGradients = new HashMap<>();

  // For dual averaging
  Map<String, Double> sumGradients = new HashMap<>();

  // Number of stochastic updates we've made so far (for determining step size).
  int numUpdates;

  // for lazy l1-reg update
  Map<String, Integer> l1UpdateTimeMap = new HashMap<>();

  // Initialize the weights
  public void init(List<Pair<String, Double>> initialization) {
    if (!weights.isEmpty())
      throw new RuntimeException("Initialization is not legal when there are non-zero weights");
    for (Pair<String, Double> pair: initialization)
      weights.put(pair.getFirst(), pair.getSecond());
  }

  // Read parameters from |path|.
  public void read(String path) {
    LogInfo.begin_track("Reading parameters from %s", path);
    try {
      BufferedReader in = IOUtils.openIn(path);
      String line;
      while ((line = in.readLine()) != null) {
        String[] pair = Lists.newArrayList(Splitter.on('\t').split(line)).toArray(new String[2]);
        weights.put(pair[0], Double.parseDouble(pair[1]));
      }
      in.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LogInfo.logs("Read %s weights", weights.size());
    LogInfo.end_track();
  }

  // Read parameters from |path|.
  public void read(String path, String prefix) {
    LogInfo.begin_track("Reading parameters from %s", path);
    try {
      BufferedReader in = IOUtils.openIn(path);
      String line;
      while ((line = in.readLine()) != null) {
        String[] pair = Lists.newArrayList(Splitter.on('\t').split(line)).toArray(new String[2]);
        weights.put(pair[0], Double.parseDouble(pair[1]));
        weights.put(prefix + pair[0], Double.parseDouble(pair[1]));
      }
      in.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LogInfo.logs("Read %s weights", weights.size());
    LogInfo.end_track();
  }

  // Update weights by adding |gradient| (modified appropriately with step size).
  public synchronized void update(Map<String, Double> gradient) {
    for (Map.Entry<String, Double> entry : gradient.entrySet()) {
      String f = entry.getKey();
      double g = entry.getValue();
      if (g * g == 0) continue;  // In order to not divide by zero

      if (l1Reg == L1Reg.LAZY) lazyL1Update(f);
      double stepSize = computeStepSize(f, g);

      if (opts.dualAveraging) {
        if (!opts.adaptiveStepSize && opts.stepSizeReduction != 0)
          throw new RuntimeException("Dual averaging not supported when " +
                  "step-size changes across iterations for " +
                  "features for which the gradient is zero");
        MapUtils.incr(sumGradients, f, g);
        MapUtils.set(weights, f, stepSize * sumGradients.get(f));
      } else {
        if (stepSize * g == Double.POSITIVE_INFINITY || stepSize * g == Double.NEGATIVE_INFINITY) {
          LogInfo.logs("WEIRD FEATURE UPDATE: feature=%s, currentWeight=%s, stepSize=%s, gradient=%s", f, getWeight(f), stepSize, g);
          throw new RuntimeException("Gradient absolute value is too large or too small");
        }
        MapUtils.incr(weights, f, stepSize * g);
        if (l1Reg == L1Reg.LAZY) l1UpdateTimeMap.put(f, numUpdates);
      }
    }
    // non lazy implementation goes over all weights
    if (l1Reg == L1Reg.NONLAZY) {
      Set<String> features = new HashSet<String>(weights.keySet());
      for (String f : features) {
        double stepSize = computeStepSize(f, 0d); // no update for gradient here
        double update = opts.l1RegCoeff * -Math.signum(MapUtils.getDouble(weights, f, opts.defaultWeight));
        clipUpdate(f, stepSize * update);
      }
    }
    numUpdates++;
    if (l1Reg == L1Reg.LAZY && opts.lazyL1FullUpdateFreq > 0 && numUpdates % opts.lazyL1FullUpdateFreq == 0) {
      LogInfo.begin_track("Fully apply L1 regularization.");
      finalizeWeights();
      System.gc();
      LogInfo.end_track();
    }
  }

  private double computeStepSize(String feature, double gradient) {
    if (opts.adaptiveStepSize) {
      MapUtils.incr(sumSquaredGradients, feature, gradient * gradient);
      // ugly - adding one to the denominator when using l1 reg.
      if (l1Reg != L1Reg.NONE)
        return opts.initStepSize / (Math.sqrt(sumSquaredGradients.get(feature) + 1));
      else
        return opts.initStepSize / Math.sqrt(sumSquaredGradients.get(feature));
    } else {
      return opts.initStepSize / Math.pow(numUpdates, opts.stepSizeReduction);
    }
  }

  /*
   * If the update changes the sign, remove the feature
   */
  private void clipUpdate(String f, double update) {
    double currWeight = MapUtils.getDouble(weights, f, 0);
    if (currWeight == 0)
      return;

    if (currWeight * (currWeight + update) < 0.0)  {
      weights.remove(f);
    } else {
      MapUtils.incr(weights, f, update);
    }
  }

  private void lazyL1Update(String f) {
    if (MapUtils.getDouble(weights, f, 0.0) == 0) return;
    // For pre-initialized weights, which have no updates yet
    if (sumSquaredGradients.get(f) == null || l1UpdateTimeMap.get(f) == null) {
      l1UpdateTimeMap.put(f, numUpdates);
      sumSquaredGradients.put(f, 0.0);
      return;
    }
    int numOfIter = numUpdates - MapUtils.get(l1UpdateTimeMap, f, 0);
    if (numOfIter == 0) return;
    if (numOfIter < 0) throw new RuntimeException("l1UpdateTimeMap is out of sync.");

    double stepSize = (numOfIter * opts.initStepSize) / (Math.sqrt(sumSquaredGradients.get(f) + 1));
    double update = -opts.l1RegCoeff * Math.signum(MapUtils.getDouble(weights, f, 0.0));
    clipUpdate(f, stepSize * update);
    if (weights.containsKey(f))
      l1UpdateTimeMap.put(f, numUpdates);
    else
      l1UpdateTimeMap.remove(f);
  }

  public synchronized double getWeight(String f) {
    if (l1Reg == L1Reg.LAZY)
      lazyL1Update(f);
    if (opts.initWeightsRandomly)
      return MapUtils.getDouble(weights, f, 2 * opts.initRandom.nextDouble() - 1);
    else {
      return MapUtils.getDouble(weights, f, opts.defaultWeight);
    }
  }

  public synchronized Map<String, Double> getWeights() { finalizeWeights(); return weights; }

  public void write(PrintWriter out) { write(null, out); }

  public void write(String prefix, PrintWriter out) {
    List<Map.Entry<String, Double>> entries = Lists.newArrayList(weights.entrySet());
    Collections.sort(entries, new ValueComparator<String, Double>(true));
    for (Map.Entry<String, Double> entry : entries) {
      double value = entry.getValue();
      out.println((prefix == null ? "" : prefix + "\t") + entry.getKey() + "\t" + value);
    }
  }

  public void write(String path) {
    LogInfo.begin_track("Params.write(%s)", path);
    PrintWriter out = IOUtils.openOutHard(path);
    write(out);
    out.close();
    LogInfo.end_track();
  }

  public void log() {
    LogInfo.begin_track("Params");
    List<Map.Entry<String, Double>> entries = Lists.newArrayList(weights.entrySet());
    Collections.sort(entries, new ValueComparator<String, Double>(true));
    for (Map.Entry<String, Double> entry : entries) {
      double value = entry.getValue();
      LogInfo.logs("%s\t%s", entry.getKey(), value);
    }
    LogInfo.end_track();
  }

  public synchronized void finalizeWeights() {
    if (l1Reg == L1Reg.LAZY) {
      Set<String> features = new HashSet<>(weights.keySet());
      for (String f : features)
        lazyL1Update(f);
    }
  }

  public Params copyParams()  {
    Params result = new Params();
    for (String feature : this.getWeights().keySet()) {
      result.weights.put(feature, this.getWeight(feature));
    }
    return result;
  }

  // copy params starting with prefix and drop the prefix
  public Params copyParamsByPrefix(String prefix)  {
    Params result = new Params();
    for (String feature : this.getWeights().keySet()) {
      if (feature.startsWith(prefix)) {
        String newFeature = feature.substring(prefix.length());
        result.weights.put(newFeature, this.getWeight(feature));
      }
    }
    return result;
  }

  public boolean isEmpty() {
    return weights.size() == 0;
  }

  public Params getRandomWeightParams()  {
    Random rand = new Random();
    Params result = new Params();
    for (String feature : this.getWeights().keySet()) {
      result.weights.put(feature, 2 * rand.nextDouble() - 1); // between -1 and 1
    }
    return result;
  }
}
