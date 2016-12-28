package edu.stanford.nlp.sempre;

import java.util.List;

/**
 * Created by joberant on 28/12/2016.
 */
public class ComputationGraphWrapper {

  static {
    System.load("/Users/joberant/Projects/sempre/libcomputation_graph_wrapper.so"); // Load native library at runtime
  }

  private native double test();

  // builds a loss node corresponding to log \sum_d p(d | x) R(d).
  public void addRewardWeightedCondLiklihood(List<Derivation> predDerivations, List<Double> rewards) {

  }


  public double scoreDerivation(Derivation deriv) {
    return test();
  }

  public static void main(String[] args) {
    System.out.println("hi");
    ComputationGraphWrapper cgw = new ComputationGraphWrapper();
    Derivation deriv = null;
    cgw.scoreDerivation(deriv);
    System.out.println("bi");
  }
}
