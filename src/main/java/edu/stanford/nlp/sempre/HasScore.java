package edu.stanford.nlp.sempre;

/**
 * Things that have a score that is a dot product of weights and features
 */
public interface HasScore {
  double getScore();
}
