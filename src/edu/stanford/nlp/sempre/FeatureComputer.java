package edu.stanford.nlp.sempre;

/**
 * A feature computer.
 *
 * Look at a derivation and add features to the feature vector.
 * A FeatureComputer should be stateless.
 *
 * Before computing features, a FeatureComputer should call
 *
 *     if (!FeatureExtractor.containsDomain(...)) return;
 *
 * to check the feature domain first.
 */
public interface FeatureComputer {

  /**
   * This function is called on every sub-Derivation.
   *
   * It should extract only the features which depend in some way on |deriv|,
   * not just on its children.
   */
  void extractLocal(Example ex, Derivation deriv);

}
