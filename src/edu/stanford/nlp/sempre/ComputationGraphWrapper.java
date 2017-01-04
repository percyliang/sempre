package edu.stanford.nlp.sempre;

import fig.basic.Option;
import jline.internal.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by joberant on 28/12/2016.
 */
public class ComputationGraphWrapper {
  public static class Options {
    @Option(gloss = "Number of features to use in dense representation")
    public int numDenseFeatures = 20000;
  }
  public static Options opts = new Options();

  static {
    System.load("/Users/joberant/Projects/sempre/bazel-bin/cg-wrapper.so"); // Load native library at runtime
  }

  // builds a loss node corresponding to log \sum_d p(d | x) R(d).
  public void addRewardWeightedCondLiklihood(List<Derivation> predDerivations, List<Double> rewards) {



  }

  // Scores a derivation with a simple dot product.
  public double scoreDerivation(Derivation deriv) {
    double[] nnInput = densifySparseFeatures(deriv.getAllFeatureVector());
    return scoreWithNetwork(nnInput);
  }

  // Convert a feature map to an array with the feature value.
  private double[] densifySparseFeatures(Map<String, Double> sparseFeatures) {
    double[] res = new double[opts.numDenseFeatures];
    for (String feature: sparseFeatures.keySet()) {
      int hash = Math.abs(feature.hashCode() % opts.numDenseFeatures);
      if (hash < 0)
        throw new RuntimeException("Negative hash");
      if (res[hash] != 0d)
        Log.warn("feature collision, value is currently %d", sparseFeatures.get(feature));
      res[hash] = sparseFeatures.get(feature);
    }
    return res;
  }

  public native void InitDynet();
  private native double scoreWithNetwork(double[] nnInput);

  public static void main(String[] args) {
    System.out.println("hi");
    ComputationGraphWrapper cgw = new ComputationGraphWrapper();
    Derivation.Builder builder = new Derivation.Builder().children(new ArrayList<>());
    Derivation deriv = builder.createDerivation();
    deriv.addFeatureWithBias("a", "b", 3.0);
    cgw.InitDynet();
    cgw.scoreDerivation(deriv);
    System.out.println("bi");
  }
}
