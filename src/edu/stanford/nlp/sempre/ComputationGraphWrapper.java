package edu.stanford.nlp.sempre;

import com.google.common.collect.Lists;
import fig.basic.LogInfo;
import fig.basic.Option;
import jline.internal.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by joberant on 28/12/2016.
 */
public class ComputationGraphWrapper {
  public static class Options {
    @Option(gloss = "Number of features to use in dense representation")
    public int numDenseFeatures = 300;
  }
  public static Options opts = new Options();

  static {
    System.load("/Users/joberant/Projects/sempre/bazel-bin/cg-wrapper.so"); // Load native library at runtime
  }

  // builds a loss node corresponding to log \sum_d p(d | x) R(d).
  public void addRewardWeightedCondLikelihood(List<Derivation> predDerivations) {
    // Compute rewards
    double[] rewards = new double[predDerivations.size()];
    for (int i = 0; i < predDerivations.size(); ++i) {
      rewards[i] = (ParserState.compatibilityToReward(predDerivations.get(i).getCompatibility()));
    }

    // Create array of features for all derivations one after the other.
    double[] features = new double[predDerivations.size() * opts.numDenseFeatures];
    int currIndex = 0;
    for (Derivation deriv: predDerivations) {
      double[] currFeatures = densifySparseFeatures(deriv.getAllFeatureVector());
      System.arraycopy(currFeatures, 0, features, currIndex, currFeatures.length);
      currIndex += currFeatures.length;
    }
    assert currIndex == predDerivations.size() * opts.numDenseFeatures;
    LogInfo.logs("Computing cond likelihood");
    computeCondLikelihoodLoss(features, rewards);
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
      if (res[hash] > 0d) {
        //LogInfo.warnings("feature collision, feature=%s, value=%f", feature, sparseFeatures.get(feature));
        hashCollisions++;
      }
      else {
        hashNonCollisions++;
      }
      res[hash] = sparseFeatures.get(feature);
    }
    return res;
  }

  public native void InitDynet(int num_params);
  private native double scoreWithNetwork(double[] nnInput);
  private native void computeCondLikelihoodLoss(double[] features, double[] rewards);

  public int hashCollisions = 0;
  public int hashNonCollisions= 0;

  public int getHashCollisions() { return hashCollisions; }
  public int getHashNonCollisions() { return hashNonCollisions; }
  public double getCollisionRatio() {return (double) hashCollisions / (hashCollisions + hashNonCollisions);}

  public static void main(String[] args) {
    System.out.println("hi");
    ComputationGraphWrapper cgw = new ComputationGraphWrapper();
    Derivation.Builder builder1 = new Derivation.Builder().children(
      new ArrayList<>()).localFeatureVector(new FeatureVector());
    Derivation.Builder builder2 = new Derivation.Builder().children(
      new ArrayList<>()).localFeatureVector(new FeatureVector());
    Derivation deriv = builder1.createDerivation();
    deriv.addFeature("a", "b", 1.0);
    deriv.compatibility = 0.2;
    Derivation deriv2 = builder2.createDerivation();
    deriv2.addFeature("d", "c", 1.0);
    deriv2.compatibility = 1.0;
    List<Derivation> predDerivations = new ArrayList<>();
    predDerivations.add(deriv);
    predDerivations.add(deriv2);
    cgw.InitDynet(opts.numDenseFeatures);
    cgw.scoreDerivation(deriv);
    cgw.addRewardWeightedCondLikelihood(predDerivations);
    System.out.println("bi");
  }
}
